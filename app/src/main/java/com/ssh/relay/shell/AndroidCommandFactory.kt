package com.ssh.relay.shell

import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.slf4j.LoggerFactory

/**
 * CommandFactory for handling exec requests (e.g. ssh-copy-id, scp, sftp).
 * Spawns /system/bin/sh -c <command> and relays I/O.
 */
class AndroidCommandFactory : CommandFactory {

    private val log = LoggerFactory.getLogger(AndroidCommandFactory::class.java)

    override fun createCommand(channel: ChannelSession, command: String): Command {
        log.info("Exec request: {}", command)
        return AndroidExecCommand(command)
    }
}
