package com.gentics.mesh;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.PublishStatusModel;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.SchemaListResponse;
import com.gentics.mesh.core.rest.schema.impl.SchemaResponse;
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.rest.client.MeshRestClient;

import io.vertx.core.json.JsonObject;

public class HugoExporter {

	public static final String NAME = "mesh-hugo-exporter";

	private String url;
	private File output;
	private String key;
	private String projectName;

	public HugoExporter setKey(String key) {
		this.key = key;
		return this;
	}

	private void setOutput(File output) {
		this.output = output;
	}

	private void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public static Options generateOptions() {
		Options options = new Options();
		options.addOption(new Option("help", "print this message"));
		options.addOption(new Option("o", "output", true, "Output directory"));
		options.addOption(new Option("url", true, "Gentics Mesh API URL (e.g.  https://demo.getmesh.io/api/v1/)"));
		options.addOption(new Option("k", "key", true, "API Key"));
		options.addOption(new Option("c", "clean", false, "Clean output directory"));
		options.addOption(new Option("p", "project", true, "Project name to export"));
		return options;
	}

	public static void main(String[] args) throws IOException {
		handleArgs(args);
	}

	public static void handleArgs(String... args) throws IOException {
		Options options = generateOptions();
		CommandLineParser parser = new DefaultParser();
		try {
			HugoExporter exporter = new HugoExporter();
			CommandLine line = parser.parse(options, args);
			if (line.getOptions().length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(NAME, options);
				System.exit(2);
			}

			// output
			File output = new File("output");
			if (line.hasOption("o")) {
				output = new File(line.getOptionValue("o"));
			}
			System.out.println("Using output: " + output);
			exporter.setOutput(output);

			// url
			if (line.hasOption("url")) {
				String url = line.getOptionValue("url");
				System.out.println("Using url: " + url);
				exporter.setUrl(url);
			} else {
				System.err.println("No connection url specified.");
				System.exit(11);
			}

			// key
			if (line.hasOption("key")) {
				String key = line.getOptionValue("key");
				System.out.println("Using key: " + key.replaceAll(".", "*"));
			} else {
				System.out.println("No key specified. Using anonymous access");
			}

			// project
			if (line.hasOption("p")) {
				String project = line.getOptionValue("p");
				System.out.println("Using project: " + project);
				exporter.setProjectName(project);
			} else {
				System.err.println("The project setting needs to be specified");
				System.exit(10);
			}

			// cleanup
			if (line.hasOption("c")) {
				exporter.cleanup();
			}

			System.out.println("-----");
			exporter.run();
			System.exit(0);
		} catch (ParseException exp) {
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
		}

	}

	private void run() throws IOException {
		export();
		System.out.println("-----");
		System.out.println("All done...");
	}

	public void cleanup() throws IOException {
		System.out.println("Cleaning output directory: " + output);
		FileUtils.deleteDirectory(output);
	}

	public void export() throws IOException {

		URL uri = new URL(url);
		boolean ssl = uri.getProtocol().startsWith("https");
		int port = uri.getPort();
		if (port == -1) {
			port = ssl ? 443 : 80;
		}

		String host = uri.getHost();

		MeshRestClient client = MeshRestClient.create(host, port, ssl);
		if (key != null) {
			client.setAPIKey(key);
		}

		SchemaListResponse schemaList = client.findSchemas().blockingGet();
		Map<String, SchemaResponse> schemas = new HashMap<>();
		schemaList.getData().forEach(s -> {
			schemas.put(s.getUuid(), s);
		});

		NodeListResponse list = client.findNodes(projectName, new NodeParametersImpl().setResolveLinks(LinkType.SHORT)).blockingGet();
		for (NodeResponse node : list.getData()) {
			Map<String, PublishStatusModel> langs = node.getAvailableLanguages();
			String schemaUuid = node.getSchema().getUuid();
			SchemaResponse schema = schemas.get(schemaUuid);
			String segment = schema.getSegmentField();

			// Handle all contents of the node
			for (String lang : langs.keySet()) {
				FieldSchema fieldSchema = schema.getField(segment);
				node = client
					.findNodeByUuid(projectName, node.getUuid(), new NodeParametersImpl().setResolveLinks(LinkType.SHORT).setLanguages(lang))
					.blockingGet();
				String path = node.getPath();
				if (fieldSchema.getType().equals("binary")) {
					Path outputPath = new File(output, path).toPath();
					outputPath.getParent().toFile().mkdirs();
					System.out.println("Exporting " + node.getUuid() + "/" + lang + " => " + outputPath);
					try (InputStream ins = client.downloadBinaryField(projectName, node.getUuid(), lang, segment).blockingGet().getStream()) {
						Files.copy(ins, outputPath, StandardCopyOption.REPLACE_EXISTING);
					}
				} else {
					StringBuffer buffer = new StringBuffer();
					JsonObject json = new JsonObject(node.getFields().toJson());
					buffer.append("---\n");
					for (String name : json.fieldNames()) {
						Object val = json.getValue(name);
						if (val instanceof String | val instanceof Integer | val instanceof Double) {
							buffer.append(name + ": \"" + val + "\"\n");
						}
						// if (val instanceof JsonObject) {
						//
						// }
						// if (val instanceof JsonArray) {
						//
						// }
					}
					buffer.append("---\n");
					Path outputPath = new File(new File(output, path), "index.md").toPath();
					outputPath.getParent().toFile().mkdirs();
					System.out.println("Exporting " + node.getUuid() + "/" + lang + " => " + outputPath);
					FileUtils.writeStringToFile(outputPath.toFile(), buffer.toString(), Charset.defaultCharset());
				}
			}
		}

	}
}
