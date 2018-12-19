javac -source 1.7 -target 1.7 -cp "%ANDROID_HOME%"/platforms/android-27/android.jar Zshui.java
copy Zshui.class zsh\jl\zshui\Zshui.class
dx --dex --output aa.dex zsh\jl\zshui\Zshui.class
adb push aa.dex /data/data
adb shell CLASSPATH=/data/data/aa.dex app_process / zsh.jl.zshui.Zshui
