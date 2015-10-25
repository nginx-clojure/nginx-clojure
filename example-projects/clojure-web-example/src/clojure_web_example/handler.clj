(ns clojure-web-example.handler
  (:require [compojure.core           :refer :all]
            [compojure.route          :as route]
            [hiccup.core              :as hiccup]
            [clojure.tools.logging    :as log]
            [nginx.clojure.core       :as ncc]
            [nginx.clojure.session]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn handle-login [uid pass session]
  "Here we can add server-side auth. In this example we'll just always authenticate
   the user successfully regardless what inputted."
  (log/debug "login with " uid ", old session :" session)
  ;; redirect to /
  {:status 302 :session (assoc session :uid uid) :headers {"Location" "/"}})


(defn- get-user [req]
  (or (-> req :session :uid) "guest"))

(def chatroom-users-channels (atom {}))

(def my-session-store
;; When worker_processes  > 1 in nginx.conf, we can not use the default in-memory session store
;; because there're more than one JVM instances and requests from the same session perhaps
;; will be handled by different JVM instances. So here we use cookie store, or nginx shared map store
;; and if we use redis to shared sessions we can try [carmine-store] (https://github.com/ptaoussanis/carmine) or 
;; [redis session store] (https://github.com/wuzhe/clj-redis-session)
  
;; use cookie store 
;(ring.middleware.session.cookie/cookie-store {:key "a 16-byte secret"})
  
;; use nginx shared map store
(nginx.clojure.session/shared-map-store "mySessionStore")
)

(def chatroom-topic)

(def sub-listener-removal-fn)

;; Because if we use embeded nginx-clojure the nginx-clojure JNI methods 
;; won't be registered until the first startup of the nginx server so we need
;; use delayed initialization to make sure some initialization work 
;; to be done after nginx-clojure JNI methods being registered.
(defn jvm-init-handler [_]
  ;; init chatroom topic
  ;; When worker_processes  > 1 in nginx.conf, there're more than one JVM instances
	;; and requests from the same session perphaps will be handled by different JVM instances. 
	;; We need setup subscribing message listener here to get chatroom messages from other JVM instances.
	;; The below graph show the message flow in a chatroom
	
	;            \-----/     (1)send  (js)    +-------+
	;            |User1| -------------------->|WorkerA|
	;            /-----\                      +-------+
	;               ^                           | |
	;               |       (3)send!            | |(2)pub!
	;               '---------------------------' |
	;                                             V
	;             \-----/   (3)send!          +-------+
	;             |User2| <------------------ |WorkerB|
	;             /-----\                     +-------+
  (def chatroom-topic (ncc/build-topic! "chatroom-topic"))
    ;; avoid duplicate adding when auto-reload namespace is enabled in dev enviroments.
  (when (bound? #'sub-listener-removal-fn) (sub-listener-removal-fn))
  (def sub-listener-removal-fn
    (ncc/sub! chatroom-topic nil 
              (fn [msg _]
                (doseq [[uid ch] @chatroom-users-channels]
                  (ncc/send! ch msg true false)))))
  nil)

(def common-html-header
  [:head 
    [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css"}]
    [:link {:rel "stylesheet" :href "//netdna.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap-theme.min.css"}]
    [:script {:src "//ajax.aspnetcdn.com/ajax/jQuery/jquery-1.11.0.min.js"}]
    [:script {:src "//netdna.bootstrapcdn.com/bootstrap/3.3.5/js/bootstrap.min.js"}]])

(defroutes app-routes
  ;; home page
  (GET "/" [:as req]
       (hiccup/html
         common-html-header
         [:div.jumbotron
          [:h1 "Nginx-Clojure Web Example"]
          [:hr]
          [:div.alert.alert-success {:role "alert"} (str "Current User: " (get-user req))]
          [:p
           [:div.btn-toolbar
            (for [[href label] {"/hello1" "HelloWorld", "/hello2" "HelloUser",
                                "/login" "Login", "/chatroom" "ChatRoom"}]
              [:a.btn.btn-primary.btn-lg {:href href :role "button"} label])]
           ]]
         ))
  (GET "/hello1" [] "Hello World!")
  (GET "/hello2" [:as req] 
       (str "Hello " (get-user req) "!"))
  ;; Websocket based chatroom
  ;; We can open two browser sessions to test it.
  (GET "/chatroom" [:as req]
       (hiccup/html
         common-html-header
           [:div.container
             [:div.panel.panel-success
              [:div.panel-heading [:h3.panel-title "Chat Room" "@" (get-user req)]]
              [:div.input-group.panel-body
                [:input#chat.form-control {:type :text :placeholder "type and press ENTER to chat"}]
                [:span.input-group-btn
                 [:button#sendbtn.btn.btn-default {:type :button} "Send!"]]
               ]
              [:div#board.list-group
               ]
              [:div.panel-footer]
             ]]
         [:script {:src "js/chat.js"}]))
  ;; chatroom Websocket server endpoint
  (GET "/chat" [:as req]
       (let [ch (ncc/hijack! req true)
             uid (get-user req)]
         (when (ncc/websocket-upgrade! ch true)
           (ncc/add-aggregated-listener! ch 500
             {:on-open (fn [ch] 
                         (log/debug "user:" uid " connected!")
                         (swap! chatroom-users-channels assoc uid ch)
                         (ncc/pub! chatroom-topic (str uid ":[enter!]")))
              :on-message (fn [ch msg]
                            (log/debug "user:" uid " msg:" msg)
                            (ncc/pub! chatroom-topic (str uid ":" msg)))
              :on-close (fn [ch reason]
                          (log/debug "user:" uid " left!")
                          (swap! chatroom-users-channels dissoc uid)
                          (ncc/pub! chatroom-topic (str uid ":[left!]")))})
           {:status 200 :body ch})))
  ;; Static files, e.g js/chat.js in dir `public`
  ;; In production environments it will be overwrited by 
  ;; nginx static files service, see conf/nginx.conf
  (route/resources "/")
  (route/not-found "Not Found"))

(defroutes auth-routes
  (POST "/login" [uid pass :as {session :session}]
        (handle-login uid pass session))
  (GET "/login" []
       (hiccup/html
         common-html-header
         [:div.container
          [:div.panel.panel-primary
           [:div.panel-heading [:h3.panel-title "Login Form"]]
           [:div.input-group.panel-body
             [:form.form-signin {:action "/login" :method "POST"}
               [:h2.form-signin-heading "Please sign in"]
               (anti-forgery-field)
               [:input#user-id.form-control {:type :text :name :uid :placeholder "User ID"}]
               [:input#user-pass.form-control {:type :password :name :pass :placeholder "Password"}]
               [:p]
               [:input#submit-btn.btn.btn-primary.btn-block {:type "submit" :value "Login!"}]
             ]]
           [:div.panel-footer]
          ]])))


(def app
  (wrap-defaults (routes auth-routes app-routes) 
                 (update-in site-defaults [:session]
                            assoc :store my-session-store)))
