package com.netresmanager.win32;

import com.sun.jna.platform.win32.ShellAPI;
import com.sun.jna.platform.win32.WinDef;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
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

    /**
     * Restores a file from the Windows Recycle Bin and renames it.
     *
     * Scans $Recycle.Bin for the file that was deleted from {@code deletedFromPath}
     * (the path at the time of deletion, i.e., the prefixed name),
     * copies it to {@code restoreToPath} (the original name/location),
     * and cleans up the recycle bin entries.
     *
     * @param deletedFromPath  the path the file had when it was deleted (prefixed name)
     * @param restoreToPath    the path to restore the file to (original name)
     * @return true if restored successfully, false otherwise
     */
    public static boolean restoreFromRecycleBin(String deletedFromPath, String restoreToPath) {
        if (!isWindows()) {
            LOG.warning("Not Windows, cannot restore from recycle bin");
            return false;
        }

        // Determine the recycle bin root from the drive letter of the deleted file.
        // Files deleted from D:\ go to D:\$Recycle.Bin, not C:\$Recycle.Bin.
        String driveLetter = "C";
        if (deletedFromPath != null && deletedFromPath.length() >= 2
            && deletedFromPath.charAt(1) == ':') {
            driveLetter = deletedFromPath.substring(0, 1);
        }

        Path recycleBinRoot = Paths.get(driveLetter + ":\\$Recycle.Bin");
        File rootDir = recycleBinRoot.toFile();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            // Fallback: try C: drive (for cases where the drive letter is wrong or the file
            // was on a removable drive that's no longer available)
            if (!"C".equalsIgnoreCase(driveLetter)) {
                recycleBinRoot = Paths.get("C:\\$Recycle.Bin");
                rootDir = recycleBinRoot.toFile();
                if (!rootDir.exists() || !rootDir.isDirectory()) {
                    LOG.warning("Recycle bin directory not found on " + driveLetter + ": or C:");
                    return false;
                }
            } else {
                LOG.warning("Recycle bin directory not found: " + recycleBinRoot);
                return false;
            }
        }

        File[] sidDirs = rootDir.listFiles();
        if (sidDirs == null) {
            LOG.warning("Cannot list recycle bin directory");
            return false;
        }

        for (File sidDir : sidDirs) {
            if (!sidDir.isDirectory()) continue;
            // Skip well-known system SIDs that won't have user files
            String dirName = sidDir.getName();
            if (dirName.equals("S-1-5-18") || dirName.equals("S-1-5-19")
                || dirName.equals("S-1-5-20")) continue;

            File[] entries = sidDir.listFiles();
            if (entries == null) continue;

            for (File entry : entries) {
                String entryName = entry.getName();
                if (!entryName.startsWith("$I")) continue;

                // Parse the $I file to get the original path
                String originalPath = parseRecycleBinInfoFile(entry);
                if (originalPath == null) continue;

                // Match against the deleted-from path
                if (!deletedFromPath.equalsIgnoreCase(originalPath)) continue;

                // Found it! Get the corresponding $R file
                String suffix = entryName.substring(2); // remove "$I" prefix
                File dataFile = new File(sidDir, "$R" + suffix);
                if (!dataFile.exists()) {
                    LOG.warning("Found $I but corresponding $R file missing: " + dataFile);
                    return false;
                }

                // Copy the data file to the restore destination
                try {
                    Path destPath = Paths.get(restoreToPath);
                    // Ensure parent directory exists
                    Path parent = destPath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(dataFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);

                    // Verify the copy succeeded
                    if (!Files.exists(destPath) || Files.size(destPath) == 0) {
                        // Check if source was non-empty and dest is empty
                        if (dataFile.length() > 0 && Files.size(destPath) == 0) {
                            LOG.warning("Restored file is empty, copy may have failed");
                            return false;
                        }
                    }

                    // Clean up recycle bin entries (best effort, may need privileges)
                    try { entry.delete(); } catch (Exception ignored) {}
                    try { dataFile.delete(); } catch (Exception ignored) {}

                    LOG.info("Successfully restored from recycle bin: "
                        + deletedFromPath + " -> " + restoreToPath);
                    return true;

                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to restore file from recycle bin", e);
                    return false;
                }
            }
        }

        LOG.info("File not found in recycle bin: " + deletedFromPath);
        return false;
    }

    /**
     * Parses a Windows Recycle Bin $I metadata file to extract the original file path.
     *
     * $I file format (Windows 10/11):
     *   Offset 0x00: 8 bytes — header (0x02 0x00 0x00 0x00 0x00 0x00 0x00 0x00)
     *   Offset 0x08: 8 bytes — file size (int64, little-endian)
     *   Offset 0x10: 8 bytes — deletion FILETIME (int64, LE)
     *   Offset 0x18: 4 bytes — path string length in characters (int32, LE)
     *   Offset 0x1C: var   — UTF-16LE encoded path string (null-terminated)
     *
     * @param infoFile the $I file
     * @return the original path string, or null if parsing fails
     */
    private static String parseRecycleBinInfoFile(File infoFile) {
        try {
            byte[] bytes = Files.readAllBytes(infoFile.toPath());
            if (bytes.length < 28) return null; // minimum header size

            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            // Skip header (8 bytes)
            buf.position(0x18);

            // Read path length in characters
            int pathLen = buf.getInt();
            if (pathLen <= 0 || pathLen > 32768) return null; // sanity check
            if (buf.position() + pathLen * 2 > bytes.length) return null; // out of bounds

            // Read the UTF-16LE path string
            byte[] pathBytes = new byte[pathLen * 2];
            buf.get(pathBytes);
            String path = new String(pathBytes, "UTF-16LE");

            // Trim trailing null characters
            int nullIdx = path.indexOf('\0');
            if (nullIdx >= 0) {
                path = path.substring(0, nullIdx);
            }

            return path.trim();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to parse $I file: " + infoFile, e);
            return null;
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
