package com.ssh.server.shell

/**
 * JNI bridge for PTY operations using native forkpty/openpty.
 * Loads the native library "ptycompat" built via CMake.
 */
object PtyCompat {

    init {
        System.loadLibrary("ptycompat")
    }

    /**
     * Fork a subprocess with a PTY.
     * Returns an IntArray: [pid, masterFd]
     * The child process will exec the given command.
     */
    external fun createSubprocess(cmd: String, args: Array<String>?, envVars: Array<String>?): IntArray

    /**
     * Set the window size on the given master PTY fd.
     */
    external fun setWindowSize(masterFd: Int, rows: Int, cols: Int)

    /**
     * Wait for the subprocess to exit.
     * Returns the exit status.
     */
    external fun waitFor(pid: Int): Int

    /**
     * Send a signal to the subprocess.
     */
    external fun sendSignal(pid: Int, signal: Int)

    /**
     * Close the master PTY file descriptor.
     */
    external fun closeFd(fd: Int)
}
