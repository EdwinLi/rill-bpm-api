package com.baidu.rigel.service.workflow.ws;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import com.sun.enterprise.glassfish.bootstrap.Constants;
import com.sun.xml.ws.tx.at.WSATConstants;
import com.sun.xml.ws.tx.dev.WSATRuntimeConfig;


/**
 * Prepare WSAT environment.
 * @author mengran
 *
 */
public class WSATContextListener implements ServletContextListener {

	protected final Logger logger = Logger.getLogger(this.getClass().getName());
	
	public static final String WSAT_CONTEXT_ROOT = "WSAT_CONTEXT_ROOT";
	
	public void contextInitialized(final ServletContextEvent sce) {

		// WSAT context root property setting----------
		String wsatContextRoot = sce.getServletContext().getContextPath(); 
		if (sce.getServletContext().getInitParameter(WSAT_CONTEXT_ROOT) != null) {
			wsatContextRoot = sce.getServletContext().getInitParameter(WSAT_CONTEXT_ROOT);
		}
		if (wsatContextRoot != null) {
			wsatContextRoot = wsatContextRoot.replaceAll("/", "");
			logger.info("Set WSAT_CONTEXT_ROOT to " + wsatContextRoot);
			System.setProperty(WSAT_CONTEXT_ROOT, wsatContextRoot);
		}
		logger.info("WSAT context root: " + WSATConstants.WSAT_CONTEXT_ROOT);
		logger.info(WSATConstants.WSAT_COORDINATORPORTTYPEPORT);
		logger.info(WSATConstants.WSAT_REGISTRATIONCOORDINATORPORTTYPEPORT);
		logger.info(WSATConstants.WSAT_REGISTRATIONREQUESTERPORTTYPEPORT);
		logger.info(WSATConstants.WSAT_PARTICIPANTPORTTYPEPORT);
		
		System.setProperty("com.sun.xml.ws.transport.http.client.HttpTransportPipe.dump", "true");
		System.setProperty("com.sun.xml.ws.transport.local.LocalTransportPipe.dump", "true");
		System.setProperty("com.sun.xml.ws.util.pipe.StandaloneTubeAssembler.dump", "true");
		System.setProperty("com.sun.xml.ws.transport.http.HttpAdapter.dump", "true");
		
		// WSAT web service context root----------
		final WSATRuntimeConfig.TxlogLocationProvider txlogLocationProvider = new WSATRuntimeConfig.TxlogLocationProvider() {
            public String getTxLogLocation() {
				try {
					String instanceRootPath = System.getProperty(Constants.INSTANCE_ROOT_PROP_NAME);
					File txLogFile = new File(instanceRootPath, "txLog/");
					txLogFile.mkdir();
					return txLogFile.getAbsolutePath();
				} catch (Exception ex) {
					logger.log(Level.WARNING, "Can not create txLog dir in instance root. Use servlet context real path, cause trouble when run two embedded-GFs in debug-mode.", ex);
					try {
						return sce.getServletContext().getRealPath("/");
					} catch (Exception e) {
						logger.log(Level.WARNING, "Can not findservlet context real path and then return null", e);
						return null;
					}
				}
            }
        };
        
        WSATRuntimeConfig.initializer()
        		.httpPort(sce.getServletContext().getInitParameter("HTTP_PORT") == null ? "8080" : sce.getServletContext().getInitParameter("HTTP_PORT"))
                .txLogLocation(txlogLocationProvider)
                .done();
	}

	public void contextDestroyed(ServletContextEvent sce) {
		
		// Do nothing.
	}

}
