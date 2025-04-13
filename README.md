# AlbertEinstein 

## Gradle Version 
gradle-8.7 

## Android SDK 
35 

## For what? 
Someone who needs to submit a graduation project wants an android application to send data to a bluetooth model, means to implement the display of the dot matrix screen. 

别人的毕设，需求是使用安卓APP向蓝牙模块发送数据，实现点阵屏幕的显示 

## Bluetooth Service 
SPS 

## LICENSE 
MIT License 

### information 
The application has one screen ``MainActivity.kt``, when bluetooth permission is granted, all paired devices will be displayed, and you can connect by tapping the device you want to connect. 
After connecting, you can send data of type ``String``. Below is the complete data. 

应用有一个界面``MainActivity.kt``，给予蓝牙权限和位置权限后将显示所有已配对的设备，点击想要连接的设备即可连接。 
连接后可发送数据，发送的数据类型为``String``，发送的完整数据内容为 

```kotlin
val data: String = TODO()
// Default or when choose "向左滚动"
"!1,$data@"
// When choose "向右滚动"
"!2,$data@"
// When press button "清空显示"
"!3, @"
```
