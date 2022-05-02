package net.folivo.trixnity.client.media


//actual suspend fun createThumbnail( // FIXME
//    file: ByteArray,
//    contentType: ContentType,
//    maxWidth: Int,
//    maxHeight: Int
//): Thumbnail =
//    withContext(Dispatchers.IO) {
//        val output = ByteArrayOutputStream()
//        val thumbnail =
//            ThumbnailUtils.extractThumbnail(BitmapFactory.decodeByteArray(file, 0, file.size), maxWidth, maxHeight)
//                .also {
//                    it.compress(Bitmap.CompressFormat.PNG, 80, output)
//                }
//        val width = thumbnail?.width
//        val height = thumbnail?.height
//        thumbnail?.recycle()
//        Thumbnail(output.toByteArray(), ContentType.Image.PNG, width, height)
//    }