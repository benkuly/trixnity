package net.folivo.trixnity.client.media

//actual suspend fun createThumbnail( // FIXME
//    file: ByteArray,
//    contentType: ContentType,
//    maxWidth: Int,
//    maxHeight: Int
//): Thumbnail =
//    withContext(Dispatchers.IO) {
//        val output = ByteArrayOutputStream()
//        val thumbnail = Thumbnails.of(ByteArrayInputStream(file))
//            .size(maxWidth, maxHeight)
//            .asBufferedImage()
//            .also {
//                ImageIO.write(it, "jpg", output)
//            }
//        val width = thumbnail?.width
//        val height = thumbnail?.height
//        Thumbnail(output.toByteArray(), ContentType.Image.JPEG, width, height)
//    }