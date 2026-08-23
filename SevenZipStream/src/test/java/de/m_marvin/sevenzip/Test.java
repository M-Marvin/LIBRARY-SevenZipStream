package de.m_marvin.sevenzip;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;

public class Test {
	
	public static void main(String[] args) throws URISyntaxException, IOException {
		
		File run = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath(), "../run");
		System.out.println(run);
		
		SegmentedImageOutputStream segstr = new SegmentedImageOutputStream(new FileOutputStream(new File(run, "segimg/1.segimg")));
		
		File[] images = new File(run, "raw/1").listFiles();
		boolean flag = false;
		for (File img : images) {
			
			BufferedImage image = ImageIO.read(img);
			if (!flag) {
				flag = true;
				segstr.setSegmentSize(image.getWidth(), image.getWidth());
			}
			segstr.writeRescaled(image);
			
		}
		
		segstr.close();
		
		SegmentedImageInputStream segin = new SegmentedImageInputStream(new FileInputStream(new File(run, "segimg/1.segimg")));
		
		BufferedImage image;
		int i = 1;
		while ((image = segin.readSegment()) != null) {
			File img = new File(run, "out/" + i++ + ".png");
			ImageIO.write(image, "PNG", img);
		}
		
		segin.close();
		
	}
	
}
