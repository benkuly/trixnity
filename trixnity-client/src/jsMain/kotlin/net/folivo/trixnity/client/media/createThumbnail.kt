package net.folivo.trixnity.client.media

// FIXME
//actual suspend fun createThumbnail(
//    file: ByteArray,
//    contentType: ContentType,
//    maxWidth: Int,
//    maxHeight: Int
//): Thumbnail {
//    data class DrawableImage(
//        val canvasImageSource: CanvasImageSource,
//        val width: Int,
//        val height: Int
//    )
//
//    val image = when (contentType.contentType) {
//        ContentType.Image.Any.contentType -> {
//            val img = document.createElement("img") as HTMLImageElement
//
//            val waitForLoad = waitForEvent("load", img)
//            img.src = URL.createObjectURL(Blob(file.toTypedArray()))
//            waitForLoad.await()
//
//            DrawableImage(img, img.width, img.height)
//        }
//        ContentType.Video.Any.contentType -> {
//            val video = document.createElement("video") as HTMLVideoElement
//            video.muted = true
//
//            val waitForLoad = waitForEvent("loadedmetadata", video)
//            video.src = URL.createObjectURL(Blob(file.toTypedArray()))
//            video.load()
//            waitForLoad.await()
//
//            val waitForSeek = waitForEvent("seeked", video)
//            video.currentTime = 0.1
//            waitForSeek.await()
//
//            DrawableImage(video, video.width, video.height)
//        }
//        else -> throw IllegalStateException("could not create image (content type not supported)")
//    }
//    val aspectRatio = image.width / image.height
//
//    val scaleFactor =
//        min(1.0, max(maxWidth, maxHeight).toDouble() / (if (aspectRatio >= 1) image.width else image.height))
//    val scaledWidth = round(image.width * scaleFactor).toInt()
//    val scaledHeight = round(image.height * scaleFactor).toInt()
//
//    val canvas = document.createElement("canvas") as HTMLCanvasElement
//    canvas.width = scaledWidth
//    canvas.height = scaledHeight
//    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
//    ctx.drawImage(image.canvasImageSource, 0.0, 0.0, scaledWidth.toDouble(), scaledHeight.toDouble())
//    val nativeBlob = Promise<Blob?> { resolve, _ -> canvas.toBlob(resolve, ContentType.Image.JPEG.toString()) }.await()
//        ?: throw IllegalStateException("could not create blob from canvas")
//
//    return Thumbnail(
//        file = Uint8Array(Response(nativeBlob).arrayBuffer().await()).unsafeCast<ByteArray>(),
//        contentType = ContentType.Image.JPEG,
//        width = scaledWidth,
//        height = scaledHeight
//    )
//}
//
//private fun waitForEvent(eventType: String, element: HTMLElement): Promise<Unit> =
//    Promise { resolve, reject ->
//        var detach: () -> Unit = {}
//        val handleError: (Event) -> Unit = {
//            detach()
//            reject((it.target as ErrorEvent?)?.error as Throwable)
//        }
//        val handleSuccess: (Event) -> Unit = {
//            detach()
//            resolve(Unit)
//        }
//        detach = {
//            element.removeEventListener(eventType, handleSuccess)
//            element.removeEventListener("error", handleError)
//        }
//        element.addEventListener(eventType, handleSuccess)
//        element.addEventListener("error", handleError)
//    }