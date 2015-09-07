(ns clojure-web-example.handler
  (:gen-class)
  (:require [compojure.core           :refer :all]
            [compojure.route          :as route]
            [hiccup.core              :as hiccup]
            [clojure.tools.logging    :as log]
            [nginx.clojure.embed      :as embed]
            [nginx.clojure.core       :as ncc]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.reload :refer [wrap-reload]]))

(defn handle-login [uid pass session]
  "Here we can add server-side auth. In this example we'll just always authenticate
   the user successfully regardless what inputted."
  (log/debug "login with " uid ", old session :" session)
  ;; redirect to /
  {:status 302 :session (assoc session :uid uid) :headers {"Location" "/"}})


(defn- get-user [req]
  (or (-> req :session :uid) "guest"))

(def chatroom-users-channels (atom {}))

(def chatroom-event-tag (int (+ 0x80 50)))

;; When worker_processes  > 1 in nginx.conf, there're more than one JVM instances
;; and requests from the same session perphaps will be handled by different JVM instances. 
;; We setup broadcast event listener here to get chatroom messages from other JVM instances.
(def init-broadcast-event-listener
  (delay
    (ncc/on-broadcast-event-decode!
      ;;tester
      (fn [{tag :tag}] 
        (= tag chatroom-event-tag))
      ;;decoder
      (fn [{:keys [tag data offset length] :as e}]
        (assoc e :data (String. data offset length "utf-8"))))
    (ncc/on-broadcast! 
      (fn [{:keys [tag data]}]
        (log/debug "onbroadcast pid=" ncc/process-id tag data @chatroom-users-channels)
        (condp = tag
          chatroom-event-tag 
            (doseq [[uid ch] @chatroom-users-channels]
              (ncc/send! ch data true false))
            nil)))))

(defroutes app-routes
  (GET "/" [:as req]
       (hiccup/html
         [:h1 "Nginx-Clojure Web Example"]
         [:hr]
         [:h2 (str "Current User: " (get-user req))]
         [:a {:href "/hello1"} "HelloWorld"]
         [:p]
         [:a {:href "/hello2"} "HelloUser"]
         [:p]
         [:a {:href "/login"} "login"]
         [:p]
         [:a {:href "/chatroom"} "chatroom"]
         ))
  (GET "/hello1" [] "Hello World!")
  (GET "/hello2" [:as req] 
       (str "Hello " (get-user req) "!"))
  ;; Websocket based chatroom
  (GET "/chatroom" [:as req]
       (hiccup/html
         [:h2 (str "Current User: " (get-user req))]
         [:hr]
         [:input#chat {:type :text :placeholder "type and press ENTER to chat"}]
         [:div#container
          [:div#board]]
         [:script {:src "js/chat.js"}]))
  (GET "/chat" [:as req]
       @init-broadcast-event-listener
       (let [ch (ncc/hijack! req true)
             uid (get-user req)]
         (when (ncc/websocket-upgrade! ch true)
           (ncc/add-aggregated-listener! ch 512
             {:on-open (fn [ch] 
                         (log/debug "user:" uid " connected!")
                         (swap! chatroom-users-channels assoc uid ch)
                         (ncc/broadcast! {:tag chatroom-event-tag :data (str uid ":[enter!]")}))
              :on-message (fn [ch msg]
                            (log/debug "user:" uid " msg:" msg)
                            ;; Broadcast message to all nginx worker processes. For more details please
                            ;; see the comments above the definition of `init-broadcast-event-listener`
                            (ncc/broadcast! {:tag chatroom-event-tag :data (str uid ":" msg)}))
              :on-close (fn [ch reason]
                          (log/debug "user:" uid " left!")
                          (swap! chatroom-users-channels dissoc uid)
                          (ncc/broadcast! {:tag chatroom-event-tag :data (str uid ":[left!]")}))})
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
         [:form {:action "/login" :method "POST"}
                (anti-forgery-field)
                [:input#user-id {:type :text :name :uid :placeholder "User ID"}]
                [:input#user-pass {:type :password :name :pass :placeholder "Password"}]
                [:input#submit-btn {:type "submit" :value "Login!"}]
                ])))


(def my-session-store
  ;; When worker_processes  > 1 in nginx.conf, we can not use the default in-memory session store
  ;; because there're more than one JVM instances and requests from the same session perphaps
  ;; will be handled by different JVM instances. So here we use cookie store another choice is
  ;; [redis session store] (https://github.com/wuzhe/clj-redis-session)
  (ring.middleware.session.cookie/cookie-store {:key "a 16-byte secret"}))


(def app
  (wrap-defaults (routes auth-routes app-routes) 
                 (update-in site-defaults [:session]
                            assoc :store my-session-store)))

(defn start-server 
  "Run an emebed nginx-clojure for debug/test usage."
  [dev?]
  (embed/run-server
    (if dev?
      ;; Use wrap-reload to enable auto-reload namespaces of modified files
      ;; DO NOT use wrap-reload in production enviroment
      (wrap-reload #'app)
      app)
    {:port 8080}))

(defn stop-server 
  "Stop the embed nginx-clojure"
  []
  (embed/stop-server))

(defn -main 
  [& args]
  (start-server (empty? args)))
