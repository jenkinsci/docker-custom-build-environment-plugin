package com.cloudbees.jenkins.plugins.okidocki;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class Container {

    OutputStream in;
    ByteArrayOutputStream out;
    ByteArrayOutputStream err;

    public Container(OutputStream in, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        this.in = in;
        this.out = out;
        this.err = err;
    }
}
