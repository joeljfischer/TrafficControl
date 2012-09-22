package snrProj.trafficControl;
import java.io.File;

import javax.swing.filechooser.FileFilter;


public class RoadFileFilter extends FileFilter {

	/* (non-Javadoc)
	 * @see javax.swing.filechooser.FileFilter#accept(java.io.File)
	 */
	@Override
	public boolean accept(File roadFile) {
		return roadFile.getName().toLowerCase().endsWith(".road")
			|| roadFile.isDirectory();
	}

	/* (non-Javadoc)
	 * @see javax.swing.filechooser.FileFilter#getDescription()
	 */
	@Override
	public String getDescription() {
		return "Road Files (*.road)";
	}
}
