package com.tkolymp.tkolympapp.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

/** Root view controller of the key window, looked up via connected scenes (`UIApplication.keyWindow` is deprecated since iOS 13). */
internal fun keyWindowRootViewController(): UIViewController? =
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstNotNullOfOrNull { it.keyWindow }
        ?.rootViewController
