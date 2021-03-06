package me.coley.recaf;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import me.coley.recaf.config.impl.ConfUpdate;
import me.coley.recaf.util.Files;
import me.coley.recaf.util.Lang;
import me.coley.recaf.util.Files.SelfReference;

/**
 * Ensures execution on the latest version.
 * 
 * @author Matt
 */
@SuppressWarnings("unused")
public class Updater {
	private final static String API_LATEST = "https://api.github.com/repos/Col-E/Recaf/releases/latest";

	public static void run(String[] args) {
		// check if updates should occur
		if (!ConfUpdate.instance().shouldCheck()) {
			return;
		}
		try {
			ConfUpdate.instance().lastCheck = System.currentTimeMillis();
			// get self data
			SelfReference self = Files.getSelf();
			if (!self.isJar()) {
				// If the execuatable context is not a runnable jar, do not
				// attempt to update.
				// Methods to do so exist should this functionality be wanted in
				// the future.
				return;
			}
			String selfVersion = Recaf.VERSION;
			// get latest data
			URL updateURL = new URL(API_LATEST);
			String content = Resources.asCharSource(updateURL, Charsets.UTF_8).read();
			JsonObject updateJson = Json.parse(content).asObject();
			// compare versions
			String latestVersion = updateJson.getString("tag_name", "1.0.0");
			if (isOutdated(selfVersion, latestVersion)) {
				Logging.info(Lang.get("update.outdated"));
				JsonArray assets = updateJson.get("assets").asArray();
				for (JsonValue assetValue : assets.values()) {
					JsonObject assetObj = assetValue.asObject();
					String file = assetObj.getString("name", "invalid");
					if (!file.endsWith(".jar")) {
						// this is not the right attachment
						continue;
					}
					String fileURL = assetObj.getString("browser_download_url", null);
					if (fileURL == null) {
						// this shouldn't happen, but just in case.
						continue;
					}
					byte[] updateContents = Resources.asByteSource(new URL(fileURL)).read();
					Files.writeFile(file, updateContents);
					Logging.error(Lang.get("update.complete"));
					runUpdated(file, args);
					return;
				}
				Logging.warn(Lang.get("update.fail.nodownload"));
				return;
			}
		} catch (URISyntaxException e) {
			Logging.error(Lang.get("update.fail.resolve"));
		} catch (IOException e) {
			Logging.error(Lang.get("update.fail.jarread"));
		} catch (Exception e) {
			Logging.error(Lang.get("update.fail.unknown") + e.getMessage());
		}
		Logging.fine(Lang.get("update.updated"));
	}

	/**
	 * Rerun recaf with updated version.
	 * 
	 * @param file
	 *            Filename of new version.
	 * @param args
	 *            Command line args to pass.
	 * @throws IOException
	 *             Thrown if the process cannot be started.
	 */
	private static void runUpdated(String file, String[] args) throws IOException {
		String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		File currentJar = new File(file);
		// Build process: java -jar recaf.jar -args
		List<String> procArgs = new ArrayList<>();
		procArgs.add(javaBin);
		procArgs.add("-jar");
		procArgs.add(currentJar.getPath());
		for (String arg : args) {
			procArgs.add(arg);
		}
		// run process and terminate current
		new ProcessBuilder(procArgs).start();
		System.out.println("Rerunning");
		System.exit(0);
	}

	/**
	 * @param self
	 *            Current version.
	 * @param latest
	 *            Latest online version.
	 * @return Is current is behind latest.
	 */
	private static boolean isOutdated(String self, String latest) {
		try {
			String[] arrSelf = self.split("\\.");
			String[] arrLatest = latest.split("\\.");
			if (arrSelf.length == arrLatest.length) {
				for (int i = 0; i < arrSelf.length; i++) {
					int iSelf = Integer.parseInt(arrSelf[i]);
					int iLatest = Integer.parseInt(arrLatest[i]);
					if (iSelf == iLatest) {
						// do nothing
						continue;
					} else {
						return iSelf < iLatest;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
