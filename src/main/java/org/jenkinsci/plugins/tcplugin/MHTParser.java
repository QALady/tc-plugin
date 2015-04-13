package org.jenkinsci.plugins.tcplugin;

import hudson.FilePath;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

/**
 * Class to parse and decompose *.mts file in its constituting parts.
 * 
 */
public class MHTParser {

	public String BOUNDARY = "boundary";
	public String CHAR_SET = "charset";
	public String CONTENT_TYPE = "Content-Type";
	public String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
	public String CONTENT_LOCATION = "Content-Location";
	public String UTF8_BOM = "=EF=BB=BF";
	public String UTF16_BOM1 = "=FF=FE";
	public String UTF16_BOM2 = "=FE=FF";

	private FilePath mhtFile;
	private FilePath outputFolder;


	public MHTParser(FilePath mhtFile, FilePath outputFolder) {
		this.mhtFile = mhtFile;
		this.outputFolder = outputFolder;
	}

	/**
	 * Main method to decompose *.mts file in its constituting parts.
	 * 
	 * @throws NullPointerException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void decompress() throws NullPointerException, IOException, InterruptedException {

		InputStreamReader rd = null;
		BufferedReader reader = null;

		String type = "";
		String encoding = "";
		String location = "";
		String filename = "";
		String charset = "utf-8";
		StringBuilder buffer = null;

		try {
			rd = new InputStreamReader(mhtFile.read());
			reader = new BufferedReader(rd);

			final String boundary = getBoundary(reader);
			if(boundary == null)
				throw new NullPointerException("Failed to find document 'boundary'. Please check *.mht file.");

			String line = null;
			while((line = reader.readLine()) != null) {
				String temp = line.trim();
				if(temp.contains(boundary)) {
					if(buffer != null) {
						writeBufferContentToFile(buffer, encoding, filename, charset);
						buffer = null;
					}
					buffer = new StringBuilder();
				} else if(temp.startsWith(CONTENT_TYPE)) {
					type = splitUsingColonSpace(temp);
				} else if(temp.startsWith(CHAR_SET)) {
					charset = getCharSet(temp);
				} else if(temp.startsWith(CONTENT_TRANSFER_ENCODING)) {
					encoding = splitUsingColonSpace(temp);
				} else if(temp.startsWith(CONTENT_LOCATION)) {
					location = temp.substring(temp.indexOf(":") + 1).trim();
					filename = getFileName(location, type);
				} else {
					if(buffer != null) {
						buffer.append(line + "\n");
					}
				}
			}
			
		} finally {		
			if(reader != null) {
				reader.close();
			}
		}
	}

	private String getCharSet(String temp) {
		String t = temp.split("=")[1].trim();
		return t.substring(1, t.length() - 1);
	}

	/**
	 * Save the file as per character set and encoding.
	 * 
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	private void writeBufferContentToFile(StringBuilder buffer, String encoding, String filename, String charset)
			throws IOException, InterruptedException {

		if(!outputFolder.exists()) {
			outputFolder.mkdirs();
		}

		byte[] content = null; 
		boolean text = true;

		if(encoding.equalsIgnoreCase("base64")) {
			content = getBase64EncodedString(buffer);
			text = false;
		} else if(encoding.equalsIgnoreCase("quoted-printable")) {
			content = getQuotedPrintableString(buffer);         
		} else {
			content = buffer.toString().getBytes();
		}

		if(!text) {
			BufferedOutputStream bos = null;
			try {
				bos = new BufferedOutputStream((new FilePath(outputFolder, filename)).write());
				bos.write(content);
				bos.flush();
			} finally {
				bos.close();
			}
		} else {
			BufferedWriter bw = null;
			try {
				System.out.println(filename);
				bw = new BufferedWriter(new OutputStreamWriter((new FilePath(outputFolder, filename)).write(), charset));
				bw.write(new String(content));
				bw.flush();
			} catch (IOException e1){
				e1.printStackTrace();

			} finally {
				try {
					bw.close();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
			}
		}
	}

	/**
	 * When the save the *.mts file with 'utf-8' encoding then it appends '=EF=BB=BF'</br>
	 * @see http://en.wikipedia.org/wiki/Byte_order_mark
	 */
	private byte[] getQuotedPrintableString(StringBuilder buffer) {

		String temp = buffer.toString().replaceAll(UTF8_BOM, "").replaceAll("=\n", "");
		return temp.getBytes();
	}

	private byte[] getBase64EncodedString(StringBuilder buffer) throws IOException {
		byte[] bytearray = DatatypeConverter.parseBase64Binary(buffer.toString());
		return bytearray;
	}

	/**
	 * Tries to get a qualified file name. If the name is not apparent it tries to guess it from the URL.
	 * Otherwise it returns 'unknown.<type>'
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	private String getFileName(String location, String type) throws IOException, InterruptedException {
		final Pattern p = Pattern.compile("(\\w|_|-)+\\.\\w+");
		String ext = "";
		String name = "";
		if(type.toLowerCase().endsWith("jpeg"))
			ext = "jpg";
		else
			ext = type.split("/")[1];

		if(location.endsWith("/")) {
			name = "main";
		} else {
			name = location.substring(location.lastIndexOf("/") + 1);

			Matcher m = p.matcher(name);
			String fname = "";
			while(m.find()) {
				fname = m.group();
			}

			if(fname.trim().length() == 0)
				name = "unknown";
			else
				return getUniqueName(fname.substring(0, fname.indexOf(".")), fname.substring(fname.indexOf(".") + 1, fname.length()));
		}
		return getUniqueName(name, ext);
	}

	/**
	 * Returns a qualified unique output file path for the parsed path.</br>
	 * In case the file already exist it appends a numarical value a continues
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	private String getUniqueName(String name,String ext) throws IOException, InterruptedException {
		int i = 1;
		FilePath file = new FilePath(outputFolder, name + "." + ext);

		if(file.exists()) {
			while(true) {
				file = new FilePath(outputFolder, name + i + "." + ext);
				if(!file.exists())
					return file.getRemote();
				i++;
			}
		}
		return file.getRemote();
	}

	private String splitUsingColonSpace(String line) {
		return line.split(":\\s*")[1].replaceAll(";", "");
	}

	/**
	 * Gives you the boundary string.
	 * 
	 * @throws IOException 
	 */
	private String getBoundary(BufferedReader reader) throws IOException {
		String line = null;

		while((line = reader.readLine()) != null) {
			line = line.trim();
			if(line.startsWith(BOUNDARY)) {
				return line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""));
			}
		}
		return null;
	}
}
