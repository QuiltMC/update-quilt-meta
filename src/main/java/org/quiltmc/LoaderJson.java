package org.quiltmc;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class LoaderJson {
	public int version;
	@SerializedName("min_java_version")
	public int minJavaVersion;
	public Libraries libraries;
	public MainClass mainClass;

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
	}

	public static class MainClass {
		public String client;
		public String server;
		public String serverLauncher;
	}
}
