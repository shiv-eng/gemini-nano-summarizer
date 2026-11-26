package com.nanodigest.ui

/** Demo inputs so the app can be tried without pasting anything. */
object SampleTexts {

    /** A WhatsApp-style export, timestamps and media placeholders included, to show chat cleaning. */
    val teamChat = """
        11/25/26, 9:02 AM - Messages and calls are end-to-end encrypted. Only people in this chat can read, listen to, or share them.
        11/25/26, 9:04 AM - Priya: Morning all. Release candidate 4.2 is on the internal track.
        11/25/26, 9:05 AM - Marco: Nice. Did the login crash fix make it in?
        11/25/26, 9:07 AM - Priya: Yes, the fix for the token refresh crash is in RC 4.2.
        11/25/26, 9:15 AM - Dev: I just saw a new crash on Android 12 when opening the camera screen. Stack trace here https://crash.example.com/issue/8812
        11/25/26, 9:16 AM - Dev: <Media omitted>
        11/25/26, 9:20 AM - Marco: How many users are affected?
        11/25/26, 9:22 AM - Dev: About 0.8% of sessions on Android 12, mostly Samsung devices.
        11/25/26, 9:30 AM - Aisha: I think we should hold the release until the camera crash is fixed. 0.8% is too high.
        11/25/26, 9:31 AM - Marco: Disagree, we promised the payments feature to the partner by Friday. We can ship and hotfix.
        11/25/26, 9:33 AM - Aisha: Shipping a known crash to millions of users for one partner deadline is risky.
        11/25/26, 9:35 AM - Marco: The partner contract has a penalty if we miss Friday.
        11/25/26, 9:41 AM - Priya: Compromise: Dev, can you get a fix by Wednesday evening?
        11/25/26, 9:43 AM - Dev: I think so. It looks like a null preview surface on resume. I can have a PR up tomorrow.
        11/25/26, 9:44 AM - Aisha: OK, if the fix is merged by Wednesday 6 PM we ship Thursday morning.
        11/25/26, 9:45 AM - Marco: Works for me. Thursday 10 AM release, staged rollout at 10%.
        11/25/26, 9:47 AM - Priya: Decision: RC 4.3 with the camera fix, release Thursday 10 AM, 10% staged rollout, full rollout Friday if crash-free rate stays above 99.5%.
        11/25/26, 9:48 AM - Dev: 👍
        11/25/26, 9:50 AM - Aisha: I'll update the release notes and tell support about the camera issue.
        11/25/26, 9:52 AM - Marco: I'll let the partner know we are on track for Friday.
        11/25/26, 10:15 AM - Priya: Reminder: QA sign-off meeting Wednesday 4 PM.
    """.trimIndent()

    val article = """
        Running large language models directly on phones changes the economics and the privacy story of mobile AI. When a model runs on the device, there is no server to pay for, no API key to protect and no per-request cost, and the text a user wants summarized never leaves their phone.

        Android ships such a model as a system service. Gemini Nano is managed by AICore, a system app that downloads the model once and shares it between every app that asks for it. That is why the model is not inside an app's APK: an app only declares that it wants a feature, and AICore fetches the weights in the background the first time that feature is needed.

        The trade-offs are real. On-device models are much smaller than their cloud counterparts, so they work best on focused tasks such as summarizing, proofreading or rewriting rather than open-ended reasoning. Their input window is limited to a few thousand tokens, which means long documents have to be split, summarized piece by piece and then combined. And not every phone can run them: support depends on the chipset, available memory and a locked bootloader, so apps need a plan for devices where the feature is unavailable.

        Perceived speed matters as much as raw speed. Streaming the output token by token lets users start reading within a fraction of a second, even if the full summary takes several seconds. Preparing the inference engine before the user taps the button removes the cold-start delay from the critical path. Together these tricks can make an app feel several times faster without the model itself getting any faster.

        For developers, the practical advice is to treat the on-device model as one backend among several. Wrap it behind your own interface, check availability before showing any AI button, label generated text clearly, and keep a fallback ready, whether that is a cloud model or a simple extractive summarizer that works everywhere.
    """.trimIndent()
}
