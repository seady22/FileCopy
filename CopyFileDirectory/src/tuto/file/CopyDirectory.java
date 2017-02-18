package tuto.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyDirectory {

	public static void main(String[] args) throws IOException {

		File srcDir = new File("/home/andy/Documents/repertoire_chef/src");
		File destDir = new File("/home/andy/Documents/repertoire_dest/target"); 

		// Make sure Directory exist

		if (!srcDir.exists()) {
			System.out.println("Directory does not exist");
		} else {
			CopyDirectory fileDemo = new CopyDirectory();
			fileDemo.copyDir(srcDir, destDir);
			System.out.println("successfully Copied");
		}
	}

	public void copyDir(File src, File dest) throws IOException {

		if (src.isDirectory()) {

			// if directory not exist,create it
			if (!dest.exists()) {

				dest.mkdir();
				System.out.println(" Directory copied from " + src + " to " + dest);
			}

			// list of directory content
			String files[] = src.list();

			for (String fileName : files) {

				// construct the src and dest file structures
				File srcFile = new File(src, fileName);
				File destFile = new File(dest, fileName);

				// recursive copy
				copyDir(srcFile, destFile);

			}
		} else {
			copyFile(src, dest);
		}
	}

	public void copyFile(File src, File dest) throws IOException {

		InputStream in = null;
		OutputStream op = null;

		try {
			// if file then copy it
			in = new FileInputStream(src);
			op = new FileOutputStream(dest);

			byte[] buffer = new byte[1024];

			int length;

			// copy the file content in the byte
			while ((length = in.read(buffer)) > 0) {
				op.write(buffer, 0, length);

			}
		} finally {
			if (in != null) {
				in.close();
			}
			if (op != null) {
				op.close();
			}

		}
		System.out.println(" File copied from " + src + " to " + dest);

	}
}




