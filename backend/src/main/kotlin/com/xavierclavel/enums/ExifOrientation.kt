package com.xavierclavel.enums

import java.awt.geom.AffineTransform
import kotlin.collections.first

enum class ExifOrientation(val value: Int) {
    NORMAL(1),
    HORIZONTAL_SYMMETRY(2),
    ROTATION_180(3),
    VERTICAL_SYMMETRY(4),
    TRANSPOSE(5),
    ROTATION_90_CW(6),
    TRANSVERSE(7),
    ROTATION_90_CCW(8);

    companion object {
        fun fromInt(i: Int) = entries.first { it.value == i }
    }

    fun requiresSwap(): Boolean = value > 4

    fun getTransform(width: Double, height: Double): AffineTransform {
        val transform = AffineTransform()

        when (this) {
            NORMAL -> { //Top left -> Normal

            }
            HORIZONTAL_SYMMETRY -> { //Top right -> Mirror horizontally
                transform.scale(-1.0, 1.0)
                transform.translate(-width, 0.0)
            }
            ROTATION_180 -> { //Bottom right -> Rotate 180,
                transform.translate(width, height)
                transform.rotate(Math.PI)
            }
            VERTICAL_SYMMETRY -> { //Bottom left -> Mirror vertically
                transform.scale(1.0, -1.0)
                transform.translate(0.0, -height)
            }
            TRANSPOSE -> { //Left top -> Mirror horizontally and rotate 90 CW
                transform.rotate(Math.PI / 2.0)
                transform.scale(1.0, -1.0)
            }
            ROTATION_90_CW -> { //Right top -> Rotate 90 CW
                transform.translate(height, 0.0)
                transform.rotate(Math.PI / 2.0)
            }
            TRANSVERSE -> { //Right bottom -> Mirror horizontally and rotate 90 CCW
                transform.scale(-1.0, 1.0)
                transform.translate(-height, 0.0)
                transform.rotate(-Math.PI / 2.0)
            }
            ROTATION_90_CCW -> { //Left bottom -> Rotate 90 CCW
                transform.translate(0.0, width)
                transform.rotate(-Math.PI / 2.0)
            }
        }

        return transform
    }
}