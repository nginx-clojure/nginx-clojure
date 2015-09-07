var chatroom = {
  connect : function(host) {
	  var socket = window['WebSocket'] || window['MozWebSocket'];
	  this.channel = new socket(host);
	  this.channel.onopen=function(){
		  chatroom.printMsg('websocket:' + host + " opened!");
		  var chatInput = document.getElementById('chat')
		  chatInput.onkeydown = function(event) {
              if (event.keyCode == 13) {
            	  var message = chatInput.value;
                  if (message != '') {
                	  chatroom.channel.send(message);
                      chatInput.value = '';
                  }
              }
          };
	  };
	  this.channel.onclose = function () {
		  document.getElementById('chat').onkeydown = null;
		  chatroom.printMsg('Info: WebSocket closed.');
      };

      this.channel.onmessage = function (msg) {
    	  chatroom.printMsg(msg.data);
      };
  },
  printMsg : function(msg) {
	  var board = document.getElementById('board');
      var p = document.createElement('p');
      p.style.wordWrap = 'break-word';
      p.innerHTML = msg;
      board.appendChild(p);
      board.scrollTop = board.scrollHeight;
  },
  
  init : function() {
	  this.connect('ws://' + window.location.host + "/chat");
  }
};

chatroom.init();