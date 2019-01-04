package zsh.jl.wxautofriend;

public class UiViewIdConst {
    //微信版本 7.0.0

    //NEXT函数
        //有搜索按钮就点搜索按钮                    next
        //有搜索输入框就点搜索输入框                 next
        //有查找微信号:xxxxxxxxxx按钮就点这个按钮     next
        //如果找不到，点击确定                      BACK
        //如果找到，点击验证按钮进入验证页面          next
        //如果是验证页面，点击发送按钮                back
        //以上都不是                                 back
    //BACK To HOME函数
        //一直按返回直到出现搜索按钮
        //如果顶部不是WEIXIN，启动微信

    //搜索按钮查找方法:先找到顶部黑色BAR，然后它的第1孩子的第0个孩子结点,如果这个孩子结点的content-desc等于搜索就认为找到
    //public static String HOME_TOP_BAR_ID = "com.tencent.mm:id/ij";//整个顶部黑色BAR 1/0是搜索按钮
    public static String HOME_FIRST_BTN_ID = "com.tencent.mm:id/qr";//首页底部第一个按钮(微信)   android.widget.ImageView
    public static String HOME_SEARCH_BTN_ID = "com.tencent.mm:id/ij";//首页搜索按钮  android.widget.ImageView
    public static String SEARCH_TOP_EDIT_INPUT_ID = "com.tencent.mm:id/ka";//搜索页面的搜索输入框  android.widget.EditText
    public static String SEARCH_RESULT_BTN_ID = "com.tencent.mm:id/bvf";//查找手机/QQ号:13764769496 android.widget.TextView
    public static String SEARCH_NOT_FOUND_TEXTVIEW_ID = "com.tencent.mm:id/d3f";//用户不存在文字，如果存在，表示没有搜索到结果 android.widget.TextView
    public static String SEARCH_CONTACTINFOUI_BTN_ID = "com.tencent.mm:id/cp";//添加到通讯录按钮 联系人页面 android.widget.TextView
    public static String SAY_HI_WITH_SNS_PERMISSIONUI_BTN_ID = "com.tencent.mm:id/jq";//发送按钮 验证页面 android.widget.Button


    public static String ACTIVITY_NAME_MAIN = ".ui.LauncherUI";                 //主页面
    public static String ACTIVITY_NAME_SEARCH = ".plugin.fts.ui.FTSMainUI";       //搜索页面
    public static String ACTIVITY_NAME_CONTACT = ".plugin.profile.ui.ContactInfoUI";       //添加联系人页面.plugin.profile.ui.ContactInfoUI
    public static String ACTIVITY_NAME_VERIFY = ".plugin.profile.ui.SayHiWithSnsPermissionUI";       //发送验证页面


    public static String RESPONSE_RESULT_WX_NEXT = "next";
    public static String RESPONSE_RESULT_WX_BACK = "back";
    public static String RESPONSE_RESULT_WX_STOP = "stop";
    public static String RESPONSE_RESULT_WX_DONE = "done";
    public static String RESPONSE_RESULT_WX_START = "start";

}
