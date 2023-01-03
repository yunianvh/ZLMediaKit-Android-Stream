package com.rtspserver

interface ClientListener {
  fun onDisconnected(client: ServerClient)
}