package com.uikit.insight

fun NewInsight(
    config: UIInsightPlayConfig,
    css: UIInsightCss = UIInsightCss()
): NewUIInsightPlay = css.New(config)

