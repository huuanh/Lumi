package com.lumi.retouch

object PortraitPipeline {
    const val VERSION = "portrait-grade-v3"

    val stages = listOf(
        PortraitStage.Transform,
        PortraitStage.BaseColor,
        PortraitStage.SkinRetouch,
        PortraitStage.FaceWarp,
        PortraitStage.Makeup,
        PortraitStage.FinalTone
    )
}

enum class PortraitStage(val label: String) {
    Transform("transform"),
    BaseColor("base color"),
    SkinRetouch("skin retouch"),
    FaceWarp("face warp"),
    Makeup("makeup"),
    FinalTone("final tone")
}
