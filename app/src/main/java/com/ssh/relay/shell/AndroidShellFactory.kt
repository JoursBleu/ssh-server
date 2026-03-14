package com.ssh.relay.shell

import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.shell.ShellFactory

class AndroidShellFactory : ShellFactory {
    override fun createShell(channel: ChannelSession): Command {
        return AndroidShellCommand()
    }
}
