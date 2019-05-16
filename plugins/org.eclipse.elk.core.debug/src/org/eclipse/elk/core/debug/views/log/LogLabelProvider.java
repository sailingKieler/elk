package org.eclipse.elk.core.debug.views.log;

import org.eclipse.elk.core.debug.ElkDebugPlugin;
import org.eclipse.elk.core.debug.LayoutExecutionInfo;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;

/**
 * Label provider for debug logs.
 */
public class LogLabelProvider extends LabelProvider implements IStyledLabelProvider {

    /** Path for image used for execution infos with logs */
    private static final String LOG_IMAGE_PATH = "/icons/log.png";
    /** Path for image used for execution infos without logs */
    private static final String NO_LOG_IMAGE_PATH = "/icons/no_log.png";

    /** The image used for execution infos with logs. */
    private Image logImage;
    /** The image used for execution infos without logs. */
    private Image noLogImage;

    /**
     * Creates an debug logs label provider.
     */
    public LogLabelProvider() {
        // loading icons for different log containers
        logImage =
                ElkDebugPlugin.imageDescriptorFromPlugin(ElkDebugPlugin.PLUGIN_ID, LOG_IMAGE_PATH).createImage();
        noLogImage =
                ElkDebugPlugin.imageDescriptorFromPlugin(ElkDebugPlugin.PLUGIN_ID, NO_LOG_IMAGE_PATH).createImage();
    }

    @Override
    public StyledString getStyledText(Object element) {
        if (element instanceof LayoutExecutionInfo) {
            return new StyledString(((LayoutExecutionInfo) element).getName());
        } else {
            return null;
        }
    }

    @Override
    public Image getImage(Object element) {
        if (element instanceof LayoutExecutionInfo) {
            return ((LayoutExecutionInfo) element).hasLogMessages()
                    ? logImage
                    : noLogImage;
        } else {
            return null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        
        if (noLogImage != null) {
            noLogImage.dispose();
            noLogImage = null;
        }
        
        if (logImage != null) {
            logImage.dispose();
            logImage = null;
        }
    }
    
}
