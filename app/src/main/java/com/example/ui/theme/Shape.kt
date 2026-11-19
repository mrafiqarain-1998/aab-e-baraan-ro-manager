package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner Radius (گولائی): rounded-xl (12px باریک گول کونے)
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // rounded-xl default (12dp)
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val RoundedCorner12 = RoundedCornerShape(12.dp)
val RoundedCorner8 = RoundedCornerShape(8.dp)
val RoundedCorner16 = RoundedCornerShape(16.dp)
