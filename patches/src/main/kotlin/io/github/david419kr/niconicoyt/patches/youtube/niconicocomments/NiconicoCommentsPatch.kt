package io.github.david419kr.niconicoyt.patches.youtube.niconicocomments

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import io.github.david419kr.niconicoyt.patches.shared.Constants.YOUTUBE
import io.github.david419kr.niconicoyt.patches.shared.ResourceType
import io.github.david419kr.niconicoyt.patches.shared.resourceLiteral
import io.github.david419kr.niconicoyt.patches.shared.resourceMappingPatch

private const val EXTENSION_CONTROLLER =
    "Lio/github/david419kr/niconicoyt/extension/niconicocomments/NiconicoCommentsController;"

@Suppress("unused")
val niconicoCommentsPatch = bytecodePatch(
    name = "Niconico-style comments (Standalone PoC)",
    description = "Adds a self-contained playback-synchronized scrolling comment overlay.",
    default = false,
) {
    compatibleWith(YOUTUBE)
    dependsOn(resourceMappingPatch)
    extendWith("extensions/extension.mpe")

    execute {
        VideoIdFingerprint.let {
            it.method.apply {
                val videoIdIndex = it.instructionMatches[1].index
                val videoIdRegister =
                    getInstruction<OneRegisterInstruction>(videoIdIndex).registerA
                addInstruction(
                    videoIdIndex + 1,
                    "invoke-static {v$videoIdRegister}, " +
                        "$EXTENSION_CONTROLLER->onVideoIdChanged(Ljava/lang/String;)V",
                )
            }
        }

        NiconicoCommentsOverlayFingerprint.let {
            it.method.apply {
                val checkCastIndex = it.instructionMatches.last().index
                val overlayRegister =
                    getInstruction<OneRegisterInstruction>(checkCastIndex).registerA
                addInstruction(
                    checkCastIndex + 1,
                    "invoke-static {v$overlayRegister}, " +
                        "$EXTENSION_CONTROLLER->initialize(Landroid/view/ViewGroup;)V",
                )
            }
        }

        val playerType = PlayerTypeEnumFingerprint.originalClassDef.type
        Fingerprint(
            definingClass = "/YouTubePlayerOverlaysLayout;",
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
            returnType = "V",
            parameters = listOf(playerType),
        ).method.addInstruction(
            0,
            "invoke-static {p1}, $EXTENSION_CONTROLLER->setPlayerType(Ljava/lang/Enum;)V",
        )

        val controlsState = ControlsStateToStringFingerprint.originalClassDef.type
        val videoState = VideoStateEnumFingerprint.originalClassDef.type
        Fingerprint(
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
            returnType = "V",
            parameters = listOf(controlsState),
            filters = listOf(
                fieldAccess(definingClass = controlsState, type = videoState),
                resourceLiteral(ResourceType.ID, "accessibility_play"),
                resourceLiteral(ResourceType.ID, "accessibility_pause"),
            ),
        ).let {
            it.method.apply {
                val videoStateField =
                    getInstruction<ReferenceInstruction>(it.instructionMatches.first().index).reference
                addInstructions(
                    0,
                    """
                        iget-object v0, p1, $videoStateField
                        invoke-static {v0}, $EXTENSION_CONTROLLER->setVideoState(Ljava/lang/Enum;)V
                    """,
                )
            }
        }

        VideoTimeReferenceFingerprint.instructionMatches
            .first()
            .getMethodCalled()
            .addInstruction(
                2,
                "invoke-static {p1, p2}, $EXTENSION_CONTROLLER->setVideoTime(J)V",
            )
    }
}
