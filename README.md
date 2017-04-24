# ndnMouse

![Icon](./app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

### Overview

It's a secure control interface for a PC over named-data-networking (NDN), using a mobile device! It also has UDP support.

### Current Features

* Mouse control: movement, left click, right click, tap-to-click
* Mouse sensitivity settings
* UDP and NDN support

### Planned Features

* NDN Security: data encryption and packet signature validation
* UDP Security: data encryption only
* Rudimentary keyboard support (for slideshow control?)

## How to Use

### Install

1. Install NFD on your [Android phone](https://play.google.com/store/apps/details?id=net.named_data.nfd) and the [PC](http://named-data.net/doc/NFD/current/INSTALL.html) on which you want to control the mouse
1. Compile and install the app using Android Studio on your Android phone

### Running

1. Make sure NFD is started on both your phone (the server/producer) and your PC (the client/consumer). Use the NFD app to start it on Android. On PC, use `nfd-start`.
1. On your phone, start the server.
1. On your PC, execute one of the Python scripts in the [pc_client](./pc_client) directory (except the TCP one, which isn't currently supported).
1. Control :mouse::exclamation:

## License
TBD