package zsh.jl.zshui;

import android.app.UiAutomation;
import android.os.HandlerThread;
import android.app.UiAutomationConnection;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.TimeoutException;

public class Zshui
{
	private static final String HANDLER_THREAD_NAME = "UiAutomatorHandlerThread";
    private final HandlerThread mHandlerThread = new HandlerThread(HANDLER_THREAD_NAME);
    private UiAutomation mUiAutomation;
	public static void main(String ... args)
	{
		Zshui z = new Zshui();
		z.test();
	}

	private void test()
	{
		if (mHandlerThread.isAlive()) {
            throw new IllegalStateException("Already connected!");
        }
        mHandlerThread.start();
        mUiAutomation = new UiAutomation(mHandlerThread.getLooper(),
                new UiAutomationConnection());
        mUiAutomation.connect();
        try {
            mUiAutomation.waitForIdle(100, 1000 * 10);
            AccessibilityNodeInfo info = mUiAutomation.getRootInActiveWindow();
            System.out.print(info.getPackageName());
         } catch (TimeoutException e) {
            e.printStackTrace();
        } finally {
            mUiAutomation.disconnect();
            mHandlerThread.quit();
        }
	}
}