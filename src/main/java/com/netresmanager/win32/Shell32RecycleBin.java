package com.netresmanager.win32;

import com.sun.jna.platform.win32.ShellAPI;
import com.sun.jna.platform.win32.WinDef;

import java.util.logging.Logger;

/**
 * JNA wrapper for Windows Shell32 SHFileOperation to move files to Recycle Bin.
 * Uses jna-platform's built-in ShellAPI.SHFILEOPSTRUCT for correct native mapping.
 */
public final class Shell32RecycleBin {

    private static final Logger LOG = Logger.getLogger(Shell32RecycleBin.class.getName());

    private Shell32RecycleBin() {}

    /**
     * Sends a file to the Windows Recycle Bin.
     * Uses the platform-provided SHFILEOPSTRUCT which has correct JNA mappings.
     */
    public static boolean sendToRecycleBin(String filePath) {
        if (!isWindows()) {
            LOG.warning("Not Windows, skipping recycle bin operation");
            return false;
        }

        try {
            ShellAPI.SHFILEOPSTRUCT fileOp = new ShellAPI.SHFILEOPSTRUCT();
            fileOp.hwnd = null;
            fileOp.wFunc = ShellAPI.FO_DELETE;
            // SHFileOperation requires double-null-terminated paths
            // For a single file: "path\0\0"
            fileOp.pFrom = filePath + "\0\0";
            fileOp.pTo = null;
            fileOp.fFlags = (short)(ShellAPI.FOF_ALLOWUNDO
                          | ShellAPI.FOF_NOCONFIRMATION
                          | ShellAPI.FOF_NOERRORUI
                          | ShellAPI.FOF_SILENT);
            fileOp.fAnyOperationsAborted = false;
            fileOp.pNameMappings = null;

            int result = com.sun.jna.platform.win32.Shell32.INSTANCE.SHFileOperation(fileOp);
            if (result != 0) {
                LOG.warning("SHFileOperation returned error code: " + result);
                return false;
            }
            if (fileOp.fAnyOperationsAborted) {
                LOG.warning("SHFileOperation was aborted by user or system");
                return false;
            }
            return true;
        } catch (Throwable e) {
            LOG.severe("Recycle bin operation failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean isWindows() {
        try {
            return System.getProperty("os.name").toLowerCase().contains("windows");
        } catch (Exception e) {
            return false;
        }
    }
}
