import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flores/flores.dart';

void main() => runApp(new MaterialApp(home: new MyApp()));

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final GlobalKey<RefreshIndicatorState> _refreshIndicatorKey =
      new GlobalKey<RefreshIndicatorState>();
  final GlobalKey<ScaffoldState> _scaffoldKey = new GlobalKey<ScaffoldState>();
  Flores _flores = new Flores();
  List<dynamic> _neighbors = [];
  final TextEditingController _textController = new TextEditingController();
  bool _isComposing = false;

  @override
  initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  initPlatformState() async {
    List<dynamic> neighbors;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      neighbors = await _flores.users;
    } on PlatformException {
      print('Failed getting neighbors');
    }
    print('users: $neighbors');
    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _neighbors = neighbors;
    });
  }

  Future<Null> _handleRefresh() {
    initPlatformState();
    final Completer<Null> completer = new Completer<Null>();
    new Timer(const Duration(seconds: 3), () {
      completer.complete(null);
    });
    return completer.future.then((_) {
      _scaffoldKey.currentState?.showSnackBar(new SnackBar(
          content: const Text('Refresh complete'),
          action: new SnackBarAction(
              label: 'RETRY',
              onPressed: () {
                _refreshIndicatorKey.currentState.show();
              })));
    });
  }

  _handleTextInput(String text) {
    _textController.clear();
    setState(() {
      _isComposing = false;
    });
    _flores.addUser(text, 'b','hi');
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
        key: _scaffoldKey,
        appBar: new AppBar(
          title: new Text('Flores example app'),
          actions: <Widget>[
            new IconButton(
              icon: new Icon(Icons.star),
              tooltip: 'Start',
              onPressed: () {
                _flores.start();
              },
            )
          ],
        ),
        body: new Column(
          children: <Widget>[
            new Row(
              children: <Widget>[
                new Expanded(
                  child: TextField(
                    controller: _textController,
                    onChanged: (String text) {
                      setState(() {
                        _isComposing = text.length > 0;
                      });
                    },
                  ),
                ),
                new IconButton(
                  icon: new Icon(Icons.send),
                  onPressed: _isComposing
                      ? () => _handleTextInput(_textController.text)
                      : null,
                )
              ],
            ),
            new Expanded(
              child: new RefreshIndicator(
                key: _refreshIndicatorKey,
                onRefresh: _handleRefresh,
                child: new ListView.builder(
                    itemBuilder: (BuildContext context, int index) =>
                        new RaisedButton(
                            onPressed: () {
                              Navigator.of(context).push(
                                  new MaterialPageRoute<Null>(
                                      builder: (BuildContext context) {
                                return new ConnectPage(
                                    _neighbors[index]['userId']);
                              }));
                            },
                            child: new Text(_neighbors[index]['userId'])),
                    itemCount: _neighbors?.length ?? 0),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ConnectPage extends StatefulWidget {
  final String neighbor;

  ConnectPage(this.neighbor);

  @override
  ConnectPageState createState() {
    return new ConnectPageState();
  }
}

class ConnectPageState extends State<ConnectPage> {
  Flores _flores = new Flores();
  bool _connectionStatus;

  @override
  initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  initPlatformState() async {
    bool connectionStatus;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      connectionStatus = await _flores.connectTo(widget.neighbor);
    } on PlatformException {
      print('Failed connecting to neighbor');
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _connectionStatus = connectionStatus;
    });
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
        appBar: new AppBar(title: new Text(widget.neighbor)),
        body: new Text(_connectionStatus.toString()));
  }
}
