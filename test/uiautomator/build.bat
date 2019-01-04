javac -encoding utf-8 -source 1.7 -target 1.7 -cp "%ANDROID_HOME%/platforms/android-19/android.jar;%ANDROID_HOME%/vendor/okhttp-3.11.0.jar;%ANDROID_HOME%/vendor/mockwebserver-3.11.0.jar;%ANDROID_HOME%/vendor/okio-1.14.0.jar;%ANDROID_HOME%/vendor/my-mockwebserver-3.11.0.jar" ./src/UiViewIdConst.java ./src/Zshui.java ./src/UnixSocket.java ./src/UnixSocketFactory.java
move /Y .\src\*.class zsh\jl\wxautofriend
dx --dex --output daemon.dex zsh\jl\wxautofriend\*.class %ANDROID_HOME%/vendor/okhttp-3.11.0.jar %ANDROID_HOME%/vendor/okio-1.14.0.jar %ANDROID_HOME%/vendor/animal-sniffer-annotations-1.10.jar %ANDROID_HOME%/vendor/my-mockwebserver-3.11.0.jar

