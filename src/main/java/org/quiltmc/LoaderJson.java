package org.quiltmc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public class LoaderJson {
	public int version;
	@SerializedName("min_java_version")
	public int minJavaVersion;
	public Libraries libraries;
	public MainClass mainClass;

	public static void addSizeAndSignatures(JsonObject json, Gson gson, String mavenId, String url, String expectedExtension, String version) {
		Library library = createFullyPopulatedLibrary(gson, mavenId, url, expectedExtension, version);

		json.addProperty("md5", library.md5);
		json.addProperty("sha1", library.sha1);
		json.addProperty("sha256", library.sha256);
		json.addProperty("sha512", library.sha512);
		json.addProperty("size", library.size);
	}

	public static Library createFullyPopulatedLibrary(Gson gson, String mavenId, String url, String expectedExtension, String version) {
		LoaderJson.Library asLibrary = new LoaderJson.Library(mavenId, url);
		LoaderJson.addSizeAndSignatures(gson, asLibrary, expectedExtension, version);

		return asLibrary;
	}

	public static void addSizeAndSignatures(Gson gson, Library library, String expectedExtension, String loaderVersion) {
		AtomicBoolean fetchedModuleData = new AtomicBoolean(false);
		String[] mavenLocation = library.name.split(":");
		String mavenFolderUrl = library.url + mavenLocation[0].replace('.', '/') + '/' + mavenLocation[1] + '/' + mavenLocation[2] + '/';

		try {
			// fetch from gradle .module file on maven so we have to do less work
			URL moduleUrl = new URL(mavenFolderUrl + mavenLocation[1] + '-' + mavenLocation[2] + ".module");

			ModuleData moduleData = ModuleData.readModuleData(gson, moduleUrl);
			ModuleData.findRuntimeFile(moduleData).ifPresent((file) -> {
				library.md5 = file.md5;
				library.sha1 = file.sha1;
				library.sha256 = file.sha256;
				library.sha512 = file.sha512;
				library.size = file.size;

				fetchedModuleData.set(true);
			});
		} catch (FileNotFoundException ignored) {
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (!fetchedModuleData.get()) {
			String jarFileUrl = mavenFolderUrl + mavenLocation[1] + '-' + mavenLocation[2] + "." + expectedExtension;

			try {
				BufferedReader md5Reader = new BufferedReader(new InputStreamReader(new URL(jarFileUrl + ".md5").openStream()));
				BufferedReader sha1 = new BufferedReader(new InputStreamReader(new URL(jarFileUrl + ".sha1").openStream()));
				BufferedReader sha256 = new BufferedReader(new InputStreamReader(new URL(jarFileUrl + ".sha256").openStream()));
				BufferedReader sha512 = new BufferedReader(new InputStreamReader(new URL(jarFileUrl + ".sha512").openStream()));

				library.md5 = md5Reader.readLine();
				library.sha1 = sha1.readLine();
				library.sha256 = sha256.readLine();
				library.sha512 = sha512.readLine();

				URL url = new URL(jarFileUrl);
				URLConnection conn = url.openConnection();
				conn.connect();
				library.size = conn.getContentLength();
			} catch (IOException e) {
				// this cannot be a hard crash because of a funny quirk: the json on quilt loader 0.17.5-beta.4 is completely broken and has ${version} instead of the real versions
				System.out.println("[WARN] Could not append additional data for loader version: " + loaderVersion + " (artifact: " + mavenLocation[1] + ")");
				e.printStackTrace();
			}
		}
	}

	public static class Libraries {
		public List<Library> client;
		public List<Library> server;
		public List<Library> common;
		public List<Library> development;
	}

	public static class Library {
		public String name;
		public String url;
		public String md5;
		public String sha1;
		public String sha256;
		public String sha512;
		public int size;

		public Library(String name, String url) {
			this.name = name;
			this.url = url;
		}
	}

	public static class MainClass {
		public String client;
		public String server;
		public String serverLauncher;
	}
}
