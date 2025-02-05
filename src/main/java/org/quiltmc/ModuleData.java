package org.quiltmc;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public class ModuleData {
	public List<ModuleVariant> variants;

	public static ModuleData readModuleData(Gson gson, URL fileUrl) throws IOException {
		InputStreamReader moduleReader = new InputStreamReader(fileUrl.openStream());
		return gson.fromJson(moduleReader, ModuleData.class);
	}

	public static Optional<ModuleVariant.ModuleFile> findRuntimeFile(ModuleData data) {
		for (ModuleVariant variant : data.variants) {
			if (variant.name.equals("runtimeElements")) {
				return Optional.of(variant.files.get(0));
			}
		}

		return Optional.empty();
	}

	public static class ModuleVariant {
		public String name;
		public List<ModuleFile> files;

		public static class ModuleFile {
			public String name;
			public String url;
			public int size;
			public String sha512;
			public String sha256;
			public String sha1;
			public String md5;
		}
	}
}
