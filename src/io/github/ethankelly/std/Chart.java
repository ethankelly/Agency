package io.github.ethankelly.std;

import io.github.ethankelly.graphs.Graph;
import io.github.ethankelly.params.Protection;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;

/**
 * Application to generate a graph to compare the results of different defence strategies in the stochastic SIRP model
 * of contagion.
 *
 * @author <a href="mailto:e.kelly.1@research.gla.ac.uk">Ethan Kelly</a>
 */
@SuppressWarnings("unused")
public class Chart extends ApplicationFrame {
	@Serial
	private static final long serialVersionUID = 1L;
	// The chart object
	private final JFreeChart chart;

	/**
	 * Class constructor.
	 *
	 * @param title          the frame title.
	 * @param filter         the value from the results we want to compare.
	 * @param protectionType which method of protection allocation the model uses.
	 * @param path           the path to the resources folder (i.e. directory containing model results).
	 * @throws IOException if the specified files do not exist.
	 */
	@SuppressWarnings("UnusedAssignment")
	public Chart(String title, Graph graph, String filter, Protection protectionType, String path, int round) throws IOException {
		super(title);
		String graphName = graph.getName();
		CategoryDataset dataset = getResultsFromCSV(filter, path, round);
		chart = createChart(dataset, filter);
		String name;
		switch (protectionType) {
			case RANDOM -> name = "Random";
			case MIXED -> name = "Mixed";
			case DETERMINISTIC -> name = "Deterministic";
			default -> throw new IllegalStateException("Unexpected value: " + filter);
		}
		// Just to make first letters upper case and rest lower case
		String[] filterArray = filter.split("\\s+");
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < filterArray.length; i++) {
			filterArray[i] = filterArray[i].substring(0, 1).toUpperCase() + filterArray[i].substring(1).toLowerCase();
			s.append(filterArray[i]);
		}
		this.writeChartToImageFile(new File(path + s + "Chart.png"));
	}

	// Creates the required comparison chart.
	private static JFreeChart createChart(CategoryDataset dataset, String filter) {
		String yAxisLabel;
		String subTitle;
		switch (filter) {
			case "INFECTED" -> {
				yAxisLabel = "Infected Agents";
				subTitle = "Number of infected agents for each defence strategy by source node (smaller the better).";
			}
			case "END TURN" -> {
				yAxisLabel = "End turn count";
				subTitle = "End turn count for each defence strategy by source node (smaller the better).";
			}
			case "PROTECTED" -> {
				yAxisLabel = "Protected Agents";
				subTitle = "Number of protected agents for each defence strategy by source node (larger the better).";
			}
			default -> throw new IllegalStateException("Unexpected value: " + filter);
		}

		JFreeChart chart = ChartFactory.createBarChart(
				"Defence Strategy Performance",
				"Source vertex",
				yAxisLabel,
				dataset);
		Font titleFont = new Font("CMU Bright", Font.BOLD, 30);
		Font subtitleFont = new Font("CMU Bright", Font.PLAIN, 15);
		Font axisFont = new Font("CMU Bright", Font.ITALIC, 18);

		chart.getTitle().setFont(titleFont);
		TextTitle sub = new TextTitle((subTitle));
		sub.setFont(subtitleFont);
		chart.addSubtitle(sub);
		chart.setBackgroundPaint(Color.WHITE);
		CategoryPlot plot = (CategoryPlot) chart.getPlot();

		plot.getDomainAxis().setLabelFont(axisFont);
		plot.getRangeAxis().setLabelFont(axisFont);

		NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
		rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
		BarRenderer renderer = (BarRenderer) plot.getRenderer();
		renderer.setDrawBarOutline(false);
		chart.getLegend().setFrame(BlockBorder.NONE);
		return chart;
	}

	/**
	 * Gets the results of a model from a CSV data file
	 *
	 * @param filter the heading of the data results we are interested in (number of infected agents, end turn count or
	 *               number of protected agents).
	 * @param path   the filepath where the method should locate the data files in order to obtain the results
	 * @param i      the current model number - used to keep track of which model we are getting the results for, this
	 *               value can be anywhere between zero and the total number of graphs generated in the model.
	 * @return the results of the model that have been obtained from the CSV file.
	 * @throws IOException if a specified file does not exist.
	 */
	public static CategoryDataset getResultsFromCSV(String filter,
	                                                String path,
	                                                int i) throws IOException {
		// Read in the model defence results and associated graph
		Reader in = new FileReader(path + "Data" + i + ".csv");
		List<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in).getRecords();

		// Add each result to our dataset
		DefaultCategoryDataset data = new DefaultCategoryDataset();
		records.forEach(record -> data.addValue(Double.parseDouble(record.get(filter)),
				record.get("STRATEGY"), record.get("OUTBREAK")));
		return data;
	}

	/**
	 * Writes the current chart to an image file.
	 *
	 * @param chartFile the file name to which to write the chart.
	 */
	public void writeChartToImageFile(File chartFile) {
		BufferedImage chartImage = this.chart.createBufferedImage(1200, 500);
		try (OutputStream out = new FileOutputStream(chartFile)) {
			ImageIO.write(chartImage, "png", out);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed writing chartFile (" + chartFile + ").", e);
		}
	}
}