# ChitChatApp
Simple chat app that uses WebSockets for communication. This is essentially a fork of [MDN example WebSocket chat app](https://github.com/mdn/samples-server/tree/master/s/websocket-chat).

The main goal of this project was to learn the basics of multithreading in Java, and WebSockets as a bonus.

Comparing to the MDN example chat app, added:
* Multithreading and state shared amongst threads
* Ability to leave chat
* More specific notifications in the chat
* Error handling and username/message text validation

Most of the functionality was added to make thread management more interesting.

To try out the app, first clone the repo. You'll need a [jar for Gson library](https://search.maven.org/artifact/com.google.code.gson/gson/2.8.6/jar). After you have Gson working, start the server (the entry point is `Server.main` method). Each client has to be opened in a separate browser tab or window.

If you notice any bugs, feel free to open an issue :)