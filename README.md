# SyncClipboard-demo
剪切板同步小助手-安卓端

## 支持系统
Android8-11


## 说明

剪切板同步依赖于Redis的Pub/Sub机制，所以需要配置公网的Redis后再打包出APK，具体方式为Fork本仓库后**转为私有仓库**，在Github-Setting-Secret中配置四个值
```
DEVICETOKEN

一串32位md5值即可，这个值的作用是标记该设备ID，避免订阅时收到了自己发的推送，陷入死循环。需要注意，各个端的DEVICETOKEN必须是不同的值
```
```
REDISADDR

带端口号的Redis地址，形如 123.123.55.44:6379
```
```
REDISPASSWORD

REDIS密码
```
```
TOPICKEY

随便起一个名字即可，这个值实现了多用户共用一个Redis的功能。就像kafka里的Topic一样，各端要监控/推送到同一个Topic才能保证消息互通
```

在Fork下的仓库配置好Secret后，进入Actions，Re-run jobs即可，等job完成后就可以下载apk了


随着安卓的权限的缩紧，想要实现后台监听剪切板变动已经不太可能，所以此插件必须ROOT后使用，太极我没用过，不知道能不能用

安装好APK后，在LSPOSED中勾选需要生效的应用，重启系统/重启目标APP即可生效，此时在目标APP中复制文字就会同步到其它订阅了同一个Topic的终端

如果想在手机上收到其它终端发来的消息，需要把该APP放在后台运行

## 奇技淫巧
1. 新增了在最近应用列表隐藏自身的功能，只需给APP加上后台锁，即可在后台一直接收到同步数据
2. 配合安卓手机上 [验证码提取器](https://github.com/tianma8023/XposedSmsCode) 插件，可实现在家的备用机/备用手机号收到的验证码同步到主手机上的效果

## 补充说明

- 务必Fork后转为私有仓库再配置Secret打包
- 务必配置好Redis密码，Redis版本保持最新，避免黑客利用
- Topic的存在使得这个程序支持多用户，但并不推荐多用户共用一个Redis


## 其它平台客户端
[SyncClipboard-Go](https://github.com/Dawnnnnnn/SyncClipboard-Go)

