copy E:\gitee\wxAutoFriend\app\src\main\java\zsh\jl\wxautofriend\Zshui.java
copy E:\gitee\wxAutoFriend\app\src\main\java\zsh\jl\wxautofriend\UnixSocket.java
copy E:\gitee\wxAutoFriend\app\src\main\java\zsh\jl\wxautofriend\UnixSocketFactory.java
javac -source 1.7 -target 1.7 -cp "%ANDROID_HOME%/platforms/android-27/android.jar;%ANDROID_HOME%/vendor/okhttp-3.10.0.jar" Zshui.java UnixSocket.java UnixSocketFactory.java
move /Y *.class zsh\jl\wxautofriend
dx --dex --output aa.dex zsh\jl\wxautofriend\*.class %ANDROID_HOME%/vendor/okhttp-3.10.0.jar %ANDROID_HOME%/vendor/okio-1.14.0.jar %ANDROID_HOME%/vendor/animal-sniffer-annotations-1.10.jar 

