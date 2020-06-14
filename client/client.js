"use strict";

let connection = null;
let clientID;

function setUsername() {
    console.log("--- Setting username... ---");
    const usernameField = document.getElementById("username");
    if (!usernameField.value || usernameField.value.match(/^\s+$/)) {
        alert("Please enter human-readable username");
        return;
    }
    var msg = {
        username: usernameField.value,
        date: Date.now(),
        id: clientID,
        type: "username",
    };
    connection.send(JSON.stringify(msg));
}

function closeConnection() {
    console.log("--- Closing connection... ---");
    connection.close();
    clientID = undefined;

    document.getElementById("manageconnection").removeChild(document.getElementById("leavechat"));

    const loginButton = document.getElementById("login");
    loginButton.setAttribute("onClick", "connect()");
    loginButton.value = "Enter chat";

    document.getElementById("message").setAttribute("disabled", "true");
    document.getElementById("send").setAttribute("disabled", "true");

    const usernameField = document.getElementById("username")
    usernameField.value = "";
    usernameField.focus();
}

function updateUserlist(userlist) {
    var ul = "";
    var i;

    for (i = 0; i < userlist.length; i++) {
        ul += userlist[i] + "<br>";
    }
    document.getElementById("userlistbox").innerHTML = ul;
}

function connect() {
    const usernameField = document.getElementById("username");
    if (!usernameField.value || usernameField.value.match(/^\s+$/)) {
        alert("Please enter human-readable username");
        return;
    }
    connection = new WebSocket("ws://localhost:80/");

    console.log(`--- Created WebSocket for client ${clientID}... ---`);

    connection.onopen = function (e) {
        const messageField = document.getElementById("message");
        messageField.removeAttribute("disabled");
        messageField.focus();
        document.getElementById("send").removeAttribute("disabled");

        const loginButton = document.getElementById("login");
        loginButton.setAttribute("onClick", "setUsername()");
        loginButton.value = "Change username";

        const leaveButton = document.createElement("input");
        leaveButton.type = "button";
        leaveButton.id = "leavechat";
        leaveButton.name = "leavechat";
        leaveButton.value = "Leave chat";
        leaveButton.setAttribute("onClick", "closeConnection()");
        document.getElementById("manageconnection").appendChild(leaveButton);

        console.log(`--- Opened connection for client... ---`);
    };

    connection.onmessage = function (evt) {
        console.log("--- Received message... ---");
        let f = document.getElementById("chatbox").contentDocument;
        let text = "";
        let msg = JSON.parse(evt.data);
        console.log("Message received: ");
        console.dir(msg);
        let time = new Date(msg.date);
        let timeStr = time.toLocaleTimeString();

        switch (msg.type) {
            case "id":
                clientID = msg.id;
                console.log(`--- Received ID for client: ${clientID}... ---`);
                setUsername();
                break;
            case "username":
                text = `<b>User <em>${msg.username}</em> signed in at ${timeStr}</b><br>`;
                break;
            case "message":
                text = `(${timeStr}) <b>${msg.username}</b>: ${msg.text}<br>`;
                break;
            case "rejectusername":
                text = `<b>Your username has been set to <em>${msg.username}</em> because the name you chose is in use.</b><br>`;
                break;
            case "userlist":
                updateUserlist(msg.users);
                break;
            case "userleft":
                updateUserlist(msg.users);
                text = `<b>User <em>${msg.username}</em> left at ${timeStr}</b><br>`;
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
        date: Date.now(),
    };
    connection.send(JSON.stringify(msg));
    messagebox.value = "";
}

function handleKey(evt) {
    console.log(evt);
    if (evt.keyCode === 13 || evt.keyCode === 14) {
        switch (evt.target.id) {
            case "message":
                if (!document.getElementById("send").disabled) {
                    send();
                }
                break;
            case "username":
                if (clientID) {
                    setUsername();
                } else {
                    connect();
                }
                break;
        }
    }
}
