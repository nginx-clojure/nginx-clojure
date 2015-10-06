var chatroom = {
  connect : function(host) {
	  var socket = window['WebSocket'] || window['MozWebSocket'];
	  this.channel = new socket(host);
	  this.channel.onopen=function(){
		  chatroom.printMsg('websocket:' + host + " opened!");
		  var chatInput = document.getElementById('chat')
		  chatInput.onkeydown = function(event) {
              if (event.keyCode == 13) {
            	  chatroom.send(chatInput);
              }
          };
          var sendBtn = document.getElementById('sendbtn');
          sendBtn.onclick = function(event) {
        	  chatroom.send(chatInput);
          }
          
	  };
	  this.channel.onclose = function () {
		  document.getElementById('chat').onkeydown = null;
		  chatroom.printMsg('websocket closed!');
      };

      this.channel.onmessage = function (msg) {
    	  chatroom.printMsg(msg.data);
      };
  },
  printMsg : function(msg) {
	  var board = document.getElementById('board');
      var p = document.createElement('a');
      var color = "success";//["success", "info", "warning", "danger"];
      if (/\[enter!\]$/.test(msg)) {
    	  color = "warning";
      }else if (/\[left!\]$/.test(msg)) {
    	  color = "danger";
      }else {
    	  color = "info";
      }
      p.className = 'list-group-item list-group-item-' + color;
      p.href="#";
      p.innerHTML = msg;
      board.appendChild(p);
      board.scrollTop = board.scrollHeight;
  },
  
  send : function(chatInput) {
	  var message = chatInput.value;
      if (message != '') {
    	  chatroom.channel.send(message);
          chatInput.value = '';
      }
  },
  
  init : function() {
	  this.connect('ws://' + window.location.host + "/chat");
  }
};

chatroom.init();