package nl.finalist.ecm.hippo.plugins.jcrrunner.job;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.JcrConstants;
import org.hippoecm.frontend.plugins.gallery.processor.ScalingGalleryProcessor;
import org.hippoecm.frontend.plugins.gallery.processor.ScalingParameters;
import org.hippoecm.repository.gallery.HippoGalleryNodeType;
import org.onehippo.forge.jcrrunner.plugins.AbstractRunnerPlugin;

public class ImageSetRefresher extends AbstractRunnerPlugin {
   private ScalingGalleryProcessor processor;
   private Map<String, ScalingParameters> configurations = new HashMap<String, ScalingParameters>();
   private Map<String, String> filenamesMap;
   private long updatesCounter = 0l;

   @Override
   public void visit(final Node node) {
      try {

         if (node.isNodeType("abdinternet:ImageSet")) {
            getLogger().info("Processing image set on node {}",
                  node.getPath());

            processImageSet(node, node.getSession());
         }
      } catch (Exception e) {
         getLogger().error(
               "An exception occurred while visiting a repository node.",
               e);
      }
   }
   
   @Override
   public void init() {
      super.init();

      getLogger().info("Running {}", this.getClass().getName());

      final String imagesFolder = "/Users/rob/Desktop/Archief"; //getConfigValue("images.folder");
      getLogger().info("Images folder: {}", imagesFolder);

      filenamesMap = new HashMap<String, String>();
      drawUpImagesInventory(imagesFolder, filenamesMap);
      getLogger().info("Found {} images in the specified location {}", filenamesMap.keySet().size(), imagesFolder);

      readImageFormatConfiguration();

      // Initialize a scaling processor with the configurations
      processor = new ScalingGalleryProcessor();
      for (Entry<String, ScalingParameters> configuration : configurations.entrySet()) {
         processor.addScalingParameters(configuration.getKey(), configuration.getValue());
      }

      getLogger().info("Scaling processor initialized with {} configurations", configurations.size());
   }

   private void readImageFormatConfiguration() {
      // Set up desired configurations
      configurations.put("hippogallery:thumbnail", new ScalingParameters(60, 40, false));
      configurations.put("abdinternet:banner", new ScalingParameters(207, 88, false));
      configurations.put("abdinternet:homeCarousel", new ScalingParameters(464, 2001, false));
      configurations.put("abdinternet:imageTeaser", new ScalingParameters(207, 100, false));
      configurations.put("abdinternet:homeColumn", new ScalingParameters(208, 60, false));
      configurations.put("abdinternet:testimonial", new ScalingParameters(684, 355, false));
      configurations.put("abdinternet:mainImage", new ScalingParameters(436, 300, false));
      configurations.put("abdinternet:landingPage", new ScalingParameters(358, 210, false));
      configurations.put("abdinternet:employee", new ScalingParameters(208, 155, false));
   }

   private void drawUpImagesInventory(final String imagesFolder, Map<String, String> filenamesMap) {
      try {
         File folder = new File(imagesFolder);
         if (folder.isDirectory()) {
            String[] folderList = folder.list();
            for (String listEntry : folderList) {
               File entry = new File(folder.getCanonicalPath() + System.getProperty("file.separator") + listEntry);
               if (entry.isFile()) {
                  filenamesMap.put(entry.getName().toLowerCase(), entry.getCanonicalPath());
               } else {
                  drawUpImagesInventory(entry.getCanonicalPath(), filenamesMap);
               }
            }
         }
      } catch (IOException e) {
         getLogger().error(
               "An exception occurred while drawing up an inventory of image files", e);
      }
   }

   @Override
   public void visitStart(final Node node) {
      super.visitStart(node);

      try {
         node.getSession().refresh(false);
      } catch (RepositoryException e) {
         getLogger().error(
               "An exception occurred while refreshing the session.", e);
      }
   }

   private void processImageSet(final Node node, final Session session) throws AccessDeniedException,
         ItemExistsException, ReferentialIntegrityException, ConstraintViolationException, InvalidItemStateException,
         VersionException, LockException, NoSuchNodeTypeException, RepositoryException, IOException {

      String originalFileName;
      
      try {
         originalFileName = node.getProperty(HippoGalleryNodeType.IMAGE_SET_FILE_NAME).getString().toLowerCase();
      } catch(javax.jcr.PathNotFoundException e) {
         getLogger().error("Node didn't have a {} property, setting it now", HippoGalleryNodeType.IMAGE_SET_FILE_NAME);
         originalFileName = node.getName().toLowerCase();
         node.setProperty(HippoGalleryNodeType.IMAGE_SET_FILE_NAME, originalFileName);
      }
      
      getLogger().info("Found imageSet node for file {}", originalFileName);

      if (filenamesMap.containsKey(originalFileName))
      {
         String path = filenamesMap.get(originalFileName);
         getLogger().info("An image file for this node is found here: {}", path);

         Calendar lastModified = new GregorianCalendar();
         Node original = getOriginal(node);

         getLogger().info("Refreshing original format");

         InputStream is = new FileInputStream(path);
         original.setProperty(JcrConstants.JCR_DATA, is);
         
         processor.initGalleryResource(original, getData(original), getMimeType(original), originalFileName,
               lastModified);

         for (String imageFormat : configurations.keySet()) {
            getLogger().info("Refreshing format {}", imageFormat);
            Node format = node.getNode(imageFormat);
            processor.initGalleryResource(format, getData(original), getMimeType(original), originalFileName,
                  lastModified);
         }

         session.save();
         updatesCounter++;
      } else {
         getLogger().info("No image file found for this node, skipping it");
      }
   }

   private String getMimeType(Node node) throws PathNotFoundException, RepositoryException {
      return node.getProperty(JcrConstants.JCR_MIMETYPE).getString();
   }

   private Node getOriginal(Node node) throws PathNotFoundException, RepositoryException {
      return node.getNode(HippoGalleryNodeType.IMAGE_SET_ORIGINAL);
   }

   private InputStream getData(Node node) throws PathNotFoundException, RepositoryException {
      return node.getProperty(JcrConstants.JCR_DATA).getBinary().getStream();
   }

   @Override
   public void destroy() {
      getLogger().info("Updated {} imageSets", updatesCounter);
      super.destroy();
   }
}
