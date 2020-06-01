"use strict";

let connection = null;
const clientID = Math.floor(Math.random() * Math.floor(1000000));

function connect() {
    connection = new WebSocket("ws://localhost:80/");

    console.log("--- Created WebSocket... ---");

    connection.onopen = function(e) {
        document.getElementById("message").removeAttribute("disabled");
        document.getElementById("send").removeAttribute("disabled");
        console.log("--- Opened connection... ---");
    };

    connection.onmessage = function(evt) {
        console.log("--- Received message... ---");
        let f = document.getElementById("chatbox").contentDocument;
        let text = "";
        let msg = JSON.parse(evt.data);
        console.log("Message received: ");
        console.dir(msg);
        let time = new Date(msg.date);
        let timeStr = time.toLocaleTimeString();
    
        switch(msg.type) {
          case "id":
            clientID = msg.id;
            setUsername();
            break;
          case "username":
            text = `<b>User <em>${msg.name}</em> signed in at ${timeStr}</b><br>`;
            break;
          case "message":
            text = `(${timeStr}) <b>${msg.name}</b>: ${msg.text}<br>`;
            break;
          case "rejectusername":
            text = `<b>Your username has been set to <em>${msg.name}</em> because the name you chose is in use.</b><br>`;
            break;
          case "userlist":
            var ul = "";
            var i;
    
            for (i=0; i < msg.users.length; i++) {
              ul += msg.users[i] + "<br>";
            }
            document.getElementById("userlistbox").innerHTML = ul;
            break;
        }
    
        if (text.length) {
          f.write(text);
          document.getElementById("chatbox").contentWindow.scrollByPages(1);
        }
    };

}

function send() {
    console.log("--- Sending message... ---");
    const messagebox = document.getElementById("message");
    var msg = {
      text: messagebox.value,
      type: "message",
      id: clientID,
      date: Date.now()
    };
    connection.send(JSON.stringify(msg));
    messagebox.value = "";
}

function handleKey(evt) {
    if (evt.keyCode === 13 || evt.keyCode === 14) {
      if (!document.getElementById("send").disabled) {
        send();
      }
    }
  }