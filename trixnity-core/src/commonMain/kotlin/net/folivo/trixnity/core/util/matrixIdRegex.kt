package net.folivo.trixnity.core.util

internal const val ID_PATTERN =
    """[@#][0-9a-z\-.=_/+]+:(?:[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|\[[0-9a-fA-F:.]{2,45}\]|[0-9a-zA-Z\-.]{1,255})(?::[0-9]{1,5})?"""
internal val matrixIdRegex = ID_PATTERN.toRegex()