package com.gentics.mesh;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import com.gentics.mesh.core.rest.node.FieldMapImpl;
import com.gentics.mesh.core.rest.node.field.impl.StringFieldImpl;
import com.gentics.mesh.core.rest.schema.StringFieldSchema;
import com.gentics.mesh.core.rest.schema.impl.StringFieldSchemaImpl;

public class HugoExporterTest {

	@Test
	public void testExporter() throws IOException {
		String[] args = new String[] { "-o=target/test", "-url", "https://demo.getmesh.io/api/v1", "--p=demo", "-c" };
		Runner.main(args);
	}

	@Test
	public void testReplaceQuotes() {
		HugoExporter exporter = new HugoExporter();
		StringBuffer buffer = new StringBuffer();
		StringFieldSchema fieldSchema = new StringFieldSchemaImpl();
		fieldSchema.setName("text");
		FieldMapImpl fields = new FieldMapImpl();
		fields.put("text", new StringFieldImpl().setString("Hello\"World"));
		exporter.extractField(buffer, fieldSchema, fields);
		String output = buffer.toString();
		assertEquals("fields.text: \"Hello\\\"World\"\n", output);
		System.out.println(output);
	}
}
