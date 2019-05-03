package com.gentics.mesh;
import java.io.IOException;

import org.junit.Test;

import com.gentics.mesh.HugoExporter;

public class HugoExporterTest {

	@Test
	public void testExporter() throws IOException {
		String[] args = new String[] { "-o=target/test", "-url", "https://demo.getmesh.io/api/v1", "--p=demo", "-c"};
		HugoExporter.main(args);
	}
}
