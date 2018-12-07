import 'dart:async';
import 'package:flores_example/app_state_container.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flores/flores.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';


void main() async {
  SharedPreferences prefs = await SharedPreferences.getInstance();
  String deviceId = prefs.getString('deviceId');
  if (deviceId == null) {
    deviceId = Uuid().v4();
    prefs.setString('deviceId', deviceId);
  }

  runApp(AppStateContainer(deviceId: deviceId, child: MyApp()));
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      AppStateContainer.of(context).getUsers();
    });
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(home: UserScreen());
  }
}

class UserScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: new AppBar(
        title: new Text('Login'),
      ),
      body: Column(
        children: <Widget>[
          Flexible(
            child: ListView(
                children: AppStateContainer.of(context)
                    .users
                    .where((u) =>
                        u['deviceId'] == AppStateContainer.of(context).deviceId)
                    .map((u) => Padding(
                          padding: const EdgeInsets.all(8.0),
                          child: RaisedButton(
                            onPressed: () async {
                              await AppStateContainer.of(context)
                                  .setLoggedInUser(u['userId'], u['message']);
                              await AppStateContainer.of(context).getUsers();
                              Navigator.of(context).push(
                                  MaterialPageRoute<Null>(
                                      builder: (BuildContext context) =>
                                          FriendScreen()));
                            },
                            child: Text(u['message']),
                          ),
                        ))
                    .toList(growable: false)),
          ),
          TextField(
              onSubmitted: (text) async {
                String userId =
                    await AppStateContainer.of(context).addUser(text);
                await AppStateContainer.of(context)
                    .setLoggedInUser(userId, text);
                await AppStateContainer.of(context).getUsers();
                Navigator.of(context).push(MaterialPageRoute<Null>(
                    builder: (BuildContext context) => FriendScreen()));
              },
              decoration: new InputDecoration.collapsed(hintText: 'Add User'))
        ],
      ),
    );
  }
}

class FriendScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    String userId = AppStateContainer.of(context).loggedInUserId;
    final users = AppStateContainer.of(context).users;
    print('FriendScreen userId: $userId users: $users');
    return Scaffold(
      appBar: new AppBar(
        title: new Text(
            'User: ${AppStateContainer.of(context).loggedInUserName} chat with...'),
      ),
      body: ListView(
          children: AppStateContainer.of(context)
              .users
              .where((u) => (u['userId'] != null && u['userId'] != userId))
              .map((u) => Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: RaisedButton(
                      onPressed: () async {
                        await AppStateContainer.of(context)
                            .getMessages(u['userId']);
                        Navigator.of(context).push(MaterialPageRoute<Null>(
                            builder: (BuildContext context) => ChatScreen(
                                  friendId: u['userId'],
                                  friendName: u['message'],
                                )));
                      },
                      child: Text(u['message'] ?? ''),
                    ),
                  ))
              .toList(growable: false)),
    );
  }
}

class ChatScreen extends StatelessWidget {
  final String friendId;
  final String friendName;

  const ChatScreen({Key key, this.friendId, this.friendName}) : super(key: key);
  @override
  Widget build(BuildContext context) {
    final messages = AppStateContainer.of(context).messages;
    return Scaffold(
      appBar: AppBar(
        title: Text(
            'User: ${AppStateContainer.of(context).loggedInUserName} chat with $friendName'),
      ),
      body: Column(
        children: <Widget>[
          Flexible(
              child: ListView.builder(
                  itemCount: messages.length,
                  reverse: true,
                  itemBuilder: (context, index) {
                    String msg = messages[index]['message'];
                    msg = msg.startsWith('!@!@!@!@')
                        ? 'Big message (${msg.length})'
                        : msg;
                    print('msg: $msg');
                    return (messages[index]['userId'] == friendId)
                        ? Row(
                            mainAxisAlignment: MainAxisAlignment.start,
                            children: <Widget>[
                                Padding(
                                    padding: const EdgeInsets.all(8.0),
                                    child: CircleAvatar(
                                      child: Text(friendName),
                                    )),
                                Flexible(child: Text(msg))
                              ])
                        : Row(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: <Widget>[
                                Flexible(child: Text(msg)),
                                Padding(
                                    padding: const EdgeInsets.all(8.0),
                                    child: CircleAvatar(
                                      child: Text(AppStateContainer.of(context)
                                          .loggedInUserName),
                                    )),
                              ]);
                  })),
          Divider(height: 1.0),
          CommentTextField(
            addComment: (message) =>
                AppStateContainer.of(context).sendMessage(friendId, message),
          )
        ],
      ),
    );
  }
}

typedef void AddComment(String comment);

class CommentTextField extends StatefulWidget {
  final AddComment addComment;

  const CommentTextField({Key key, this.addComment}) : super(key: key);

  @override
  CommentTextFieldState createState() {
    return new CommentTextFieldState();
  }
}

class CommentTextFieldState extends State<CommentTextField> {
  final TextEditingController _textController = new TextEditingController();
  FocusNode _focusNode;
  bool _isComposing = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(children: <Widget>[
      Flexible(
        child: new TextField(
          maxLength: null,
          keyboardType: TextInputType.multiline,
          controller: _textController,
          focusNode: _focusNode,
          onChanged: (String text) {
            setState(() {
              _isComposing = text.trim().isNotEmpty;
            });
          },
          onSubmitted: (String text) => _handleSubmitted(context, text, false),
          decoration: new InputDecoration.collapsed(hintText: 'Send message'),
        ),
      ),
      Container(
          margin: new EdgeInsets.symmetric(horizontal: 4.0),
          child: IconButton(
            icon: new Icon(Icons.send),
            onPressed: _isComposing
                ? () => _handleSubmitted(context, _textController.text, false)
                : null,
          )),
      Container(
          margin: new EdgeInsets.symmetric(horizontal: 4.0),
          child: IconButton(
            icon: new Icon(Icons.airplanemode_active),
            onPressed: _isComposing
                ? () => _handleSubmitted(context, _textController.text, true)
                : null,
          )),
    ]);
  }

  Future<Null> _handleSubmitted(
      BuildContext context, String text, bool isHuge) async {
    _textController.clear();
    setState(() {
      _isComposing = false;
    });
    if (isHuge) {
      List x = [];
      for (int i = 0; i < 1000; i++) x.add(text);
      text = '!@!@!@!@${x.join()}';
    }
    widget.addComment(text);
    _focusNode.unfocus();
  }
}
