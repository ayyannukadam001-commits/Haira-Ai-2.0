package com.haira.ai.ai
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class ModelDownloader(private val context:Context){
 fun start(item:ModelItem):Long{
  val filename=Uri.parse(item.url).lastPathSegment ?: (item.name+".gguf")
  val request=DownloadManager.Request(Uri.parse(item.url))
   .setTitle(item.name)
   .setDescription("Downloading GGUF model for HAIRA AI")
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setAllowedOverMetered(false)
   .setAllowedOverRoaming(false)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"HAIRA_Models/"+filename)
  val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  return manager.enqueue(request)
 }
}
