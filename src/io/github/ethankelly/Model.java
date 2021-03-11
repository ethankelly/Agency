package io.github.ethankelly;


import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * The {@code Model} class contains all methods involved in storing and updating the state of play. For instance, a
 * method for getting the currently burning vertices (based on a given probability of propagation) and for updating the
 * state of the graph. The model starts with only the source node as infected, any vertices that do not have a path to
 * the source node as protected and any others as susceptible. As the model progresses, if any vertices have been
 * influenced to the level required to deem them immune to the disease (perhaps by a vaccine), then they move to the
 * protected state. Further, any transmissions that occur based on the probability of transmission and the protection
 * rating of the agents the contagion is currently adjacent to have their states updated accordingly. Then, another
 * defence turn is played and so on, until no more moves can be made. This class contains methods that check for this
 * instance after every turn and thereby end the simulation. The methods here are passed probabilities which dictate
 * infection dynamics, depending on the particular context we want to consider.
 *
 * @author <a href="mailto:e.kelly.1@research.gla.ac.uk">Ethan Kelly</a>
 */
@SuppressWarnings("unused")
public class Model {
	// Defence strategies
	public static final int PROXIMITY = 0;
	public static final int DEGREE = 1;
	public static final int PROTECTION = 2;
	// Protection rating determination
	public static final int RANDOM = 0;
	public static final int MIXED = 1;
	public static final int DETERMINISTIC = 2;

	// Ensures readable results aren't printed for large graphs
	static boolean printReadable;
	// Underlying graph the model runs on
	private Graph graph;
	// Agents assigned to each graph vertex
	private List<Agent> agents;

	/**
	 * Class constructor.
	 *
	 * @param graph the graph we use in the model we are creating.
	 */
	public Model(Graph graph) {
		this.graph = graph;
		// Logical test to see whether printing to readable output is feasible
		printReadable = graph.getNumVertices() < 25;
		// This ensures only one instance is created, which we then update,
		// rather than creating multiple instances and so on.
		this.agents = assignAgents(graph.getNumVertices());
	}

	public static int inputVertices() {
		Scanner s = new Scanner(System.in);
		System.out.println("Enter number of vertices:");
		return s.nextInt();  // Return user input
	}

	public static int inputEdges() {
		Scanner s = new Scanner(System.in);
		System.out.println("Enter number of edges:");
		return s.nextInt(); // Return user input
	}

	public static String[] runModels(Model mProximity, Model mDegree, Model mProtection, int protectionType) {

		// Print headings for data CSV file
		StringBuilder data = new StringBuilder();
		StringBuilder readable = new StringBuilder();
		data.append("OUTBREAK,STRATEGY,END TURN,SUSCEPTIBLE,INFECTED,RECOVERED,PROTECTED\n");

		// Set defence and infection probabilities to 1
		double totalDefence = 1.0, probInfection = 1.0;
		// If graph is small enough, print readable results
		readable.append(mProximity.printModelInfo(totalDefence, probInfection));
		for (int i = 0; i < mProximity.getNumVertices(); i++) {
			// Initialise model agents
			mProximity.initialiseAgents(i, protectionType);
			mDegree.initialiseIdenticalModel(i, mProximity);
			mProtection.initialiseIdenticalModel(i, mProximity);

			// If we are generating a readable file, print agent information

			readable.append("\n## Outbreak: ").append(i).append("\n").append(printAgents(mProximity));

			String[] proximityResult = mProximity.runTest(totalDefence, probInfection, PROXIMITY);
			String[] degreeResult = mDegree.runTest(totalDefence, probInfection, DEGREE);
			String[] protectionResult = mProtection.runTest(totalDefence, probInfection, PROTECTION);

			// Print the results to a more machine-readable file.
			data.append(proximityResult[0]).append("\n").append(degreeResult[0]).append("\n")
					.append(protectionResult[0]).append("\n");

			readable.append(proximityResult[1]).append("\n").append(degreeResult[1]).append("\n")
					.append(protectionResult[1]).append("\n");
		}
		return new String[]{String.valueOf(data), String.valueOf(readable)}; // 0 - data, 1 - readable
	}

	// Gets the results of a model from a CSV data file
	static CategoryDataset getResults(String filter, int protectionType) throws IOException {
	    DefaultCategoryDataset data = new DefaultCategoryDataset();
	    String name = "";
	    switch (protectionType) {
	        case RANDOM -> name = "Random";
	        case MIXED -> name = "Mixed";
	        case DETERMINISTIC -> name ="Deterministic";
	    }
	    // Read in the model defence results and associated graph
	    Reader in = new FileReader("data/" + name + "/" + name + "Data.csv");
	    List<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in).getRecords();

	    // Add each result to our dataset
	    records.forEach(record -> data.addValue(
	            Double.parseDouble(record.get(filter)),
	            record.get("STRATEGY"),
	            record.get("OUTBREAK")));
	    return data;
	}

	public String printModelInfo(double totalDefence, double probInfection) {
		Graph g = this.getGraph();
		StringBuilder s = new StringBuilder();
		s.append("# Readable results of SIRP defence strategies on a random graph\n");
		// Print information about the generated graph and modelling values
		DecimalFormat df = new DecimalFormat("0.00");

		if (!g.getName().equals("")) {
			s.append("## Generating ").append(g.getName()).append(" Graph:");
		} else {
			s.append("## Reading graph from csv file");
		}

		if (!g.getName().equals("")) {
			s.append("\n\nGraph generator has generated the specified graph with the following parameters:\n")
					.append("\n * Type of graph: ").append(g.getName());
		} else s.append("\n\nGraph from file has the following attributes:");
				s.append("\n * Number of vertices: ").append(g.getNumVertices()).append("\n * Number of edges: ")
				.append(g.getNumEdges()).append("\n * Probability: ").append(g.getNumEdges()).append(" / ")
				.append("(").append(g.getNumVertices()).append(" * (").append(g.getNumVertices())
				.append(" - 1) / 2) = ").append(Double.parseDouble(df.format((double) g.getNumEdges()
		                                                                                                                                                                                                                                                                                                                                                                                                                                          / (g.getNumVertices() * (g.getNumVertices() - 1) / 2.0)))).append("\n * Random generator seed: ").append(GraphGenerator.getSeed()).append("\n");
		s.append("\nThe generated graph is represented using the following adjacency matrix:\n\n");
		s.append(this.getGraph());

		s.append("\n## Model values");
		s.append("\nThe values used in the model are:\n * Total defence quota each turn: ")
				.append(totalDefence).append("\n * Probability with which the infection propagates: ")
				.append(probInfection);

		return String.valueOf(s);
	}

	public void initialiseAgents(int outbreak, int protectionType) {
		// Initialise a list of agents given the starting state of the graph..
		for (int j = 0; j < this.getNumVertices(); j++) {
			this.getAgents().set(j, new Agent(j, 0, 0, State.SUSCEPTIBLE));
			this.getAgents().get(j).setPeril(this.perilRating(this.getAgents().get(j), new int[] {outbreak}, this.getGraph()));
			this.getAgents().get(j).setProtection(this.protectionRating(this.getAgents().get(j), protectionType));
			this.getAgents().get(j).setState(this.findState(this.getAgents().get(j), new int[] {outbreak}));
		}
		this.getAgents().get(outbreak).setState(State.INFECTED);
	}

	public void initialiseIdenticalModel(int outbreak, Model that) {
		for (int j = 0; j < this.getNumVertices(); j++) {
			this.getAgents().set(j, new Agent(j, 0, 0, State.SUSCEPTIBLE));
			this.getAgents().get(j).setPeril(that.getAgents().get(j).getPeril());
			this.getAgents().get(j).setProtection(that.getAgents().get(j).getProtection());
			this.getAgents().get(j).setState(this.findState(this.getAgents().get(j), new int[] {outbreak}));
		}
		this.getAgents().get(outbreak).setState(State.INFECTED);
	}

	/**
	 * Runs a test model, with a particular graph and outbreak, so that we can test and monitor the behaviours of each
	 * method and verify that the model runs as expected by printing to a file in a human-readable way.
	 *
	 * @param totalDefence           the total amount of protection improvement that can be distributed to susceptible
	 *                               vertices each defensive turn.
	 * @param probabilityOfInfection the probability with which the infection propagates.
	 * @param whichDefence           the choice of defence strategy.
	 */
	public String[] runTest(double totalDefence, double probabilityOfInfection, int whichDefence) {
		StringBuilder data = new StringBuilder();
		StringBuilder readable = new StringBuilder();

		int outbreak = this.getInfected().get(0).getVertex(); // TODO more than one outbreak?
		data.append(outbreak).append(",");
		switch (whichDefence) {
			case PROXIMITY -> {
				readable.append("\n#### Proximity to Infection Defence\n");
				data.append("PROXIMITY,");
			}
			case DEGREE -> {
				readable.append("\n#### Greatest Degree Defence\n");
				data.append("DEGREE,");
			}
			case PROTECTION -> {
				readable.append("\n#### Highest Protection Defence\n");
				data.append("PROTECTION,");
			}
			default -> throw new IllegalStateException("Unexpected value: " + whichDefence);
		}

		readable.append(this.printSIRP());

		int turn = 0;
		while (true) {
			if (!this.getSusceptible().isEmpty()) {
				// Print the strategy we performed. Each increase is to
				// 2 dp when printed, but maintains full length in usage.
				double[] strategy = this.runDefence(totalDefence, whichDefence);

				double[] strategyToPrint = new double[strategy.length];
				DecimalFormat df = new DecimalFormat("0.00");
				int i = 0;
				for (double d : strategy) strategyToPrint[i++] = Double.parseDouble(df.format(d));
				readable.append("\n_Strategy:_ ").append(Arrays.toString(strategyToPrint)).append("\n");
				readable.append(this.printSIRP());

				turn++;
			} else {
				readable.append("\n__Nothing more to protect.__\nEnding model with ")
						.append(this.getProtected().size()).append(" protected and ")
						.append(this.getInfected().size()).append(" infected vertices in ")
						.append(turn).append(turn == 1 ? " turn.\n" : " turns.\n");
				break;
			}
			if (!this.getSusceptible().isEmpty()) {
				List<Agent> toInfect = this.findNextBurning(probabilityOfInfection);
				if (!toInfect.isEmpty()) {
					readable.append("\n_Infecting:_ ");
					toInfect.stream().map(agent -> agent.getVertex() + " ").forEach(readable::append);
					readable.append("\n");
				} else {
					readable.append("\n_Nothing infected._");
				}
				readable.append(this.printSIRP());
				turn++;
			} else {
					readable.append("\n__Nothing more to infect.__\nEnding model with ")
							.append(this.getProtected().size()).append(" protected and ")
							.append(this.getInfected().size()).append(" infected vertices in ")
							.append(turn).append(turn == 1 ? " turn.\n" : " turns.\n");
				break;
			}
		}
		data.append(turn).append(",")
				.append(this.getSusceptible().size()).append(",")
				.append(this.getInfected().size()).append(",")
				.append(this.getRecovered().size()).append(",")
				.append(this.getProtected().size());
		return new String[]{String.valueOf(data), String.valueOf(readable)};
	}

	/**
	 * Given an agent, the current fires and the graph the model uses, we return a peril rating. This method can be used
	 * frequently to update the peril when the graph has been updated (new infections, recoveries and protections have
	 * taken place) and the peril rating itself is thus a true reflection of the danger level the agent is facing.
	 *
	 * @param agent the agent we want to calculate danger relative to.
	 * @param fires agents that are currently infected by the contagion.
	 * @param g     the graph associated with the model.
	 * @return the updated peril rating, based on proximity to infection (0 - no danger, 1 - imminent danger).
	 */
	public double perilRating(Agent agent, int[] fires, Graph g) {
		double peril;

		int[] leastDistancePath = g.shortestPath(agent.getVertex());
		double leastDist = leastDistancePath[fires[0]];
		//TODO make this work for more than one fire location - find closest fire, find shortest path to it

		if (leastDist == 0) peril = 1.0;
		else peril = leastDist == Integer.MAX_VALUE ? 0.0 : 1 / leastDist;

		agent.setPeril(peril);
		return peril;
	}

	/**
	 * For a given agent, we need to update their protection rating frequently, which this model does by multiplying a
	 * baseline random number between 0 and 1 by the current peril rating, which means the protection rating increases
	 * with proximity to infected agents.
	 *
	 * @param agent          the agent to determine a protection rating for.
	 * @param protectionType the method of protection determination we use (fully random, fully deterministic or mixed)
	 * @return the updated protection rating attribute.
	 */
	public double protectionRating(Agent agent, int protectionType) {
		double peril = agent.getPeril();
		double baseline = Math.random();
		double protection;

		switch (protectionType) {
			case Model.RANDOM -> // Fully random protection rating
					protection = baseline;
			case Model.MIXED -> { // Partially random, partially deterministic protection rating
				if (baseline * (1 / peril) > 1) protection = 1;
				else protection = (peril == 0) ? baseline : (baseline * (1 / peril));
			}
			case Model.DETERMINISTIC -> { // Fully deterministic protection rating
				if (peril < 1) protection = peril;
				else protection = 0.999;
			}
			default -> throw new IllegalStateException("Unexpected value: " + protectionType);
		}

		return protection;
	}

	/**
	 * Given an agent and the agents that are currently infected, we determine the state based on whether a path exists
	 * between the agent and an infected vertex. If no such path exists, we say the agent is protected. If one exists,
	 * they are susceptible. If the distance to an infected vertex is zero, then they are infected themselves. If they
	 * have been infected for a given number of turns, the agent becomes recovered for a further given number of turns.
	 *
	 * @param agent the agent to determine the state of.
	 * @param fires all currently infected (burning) vertices.
	 * @return the updated state of the agent we have determined for the current model situation.
	 */
	public State findState(Agent agent, int[] fires) {
		int vertex = agent.getVertex();
		double peril = this.getAgents().get(vertex).getPeril();
		double protection = this.getAgents().get(vertex).getProtection();
		State toSet = State.SUSCEPTIBLE;

		for (int fire : fires) {
			if (vertex == fire) {
				toSet = State.INFECTED;
				break;
			}
		}
		if (protection == 1.0 || peril == 0) {
			toSet = State.PROTECTED;
		}

		agent.setState(toSet);
		return toSet;
	}

	/**
	 * We have several (heuristic) defence approaches to the stochastic model that this class instantiates. We choose
	 * one of the defence strategies currently available:
	 * <ul>
	 *      <li> Defend based on proximity to fire, breaking ties by choosing the highest degree vertex;
	 *      <li> Defend based on highest degree vertices, breaking ties with greatest proximity to fire; and
	 *      <li> Defend the agents with the highest protection ratings.
	 * </ul>
	 * <p>
	 * In the proximity approach, we find the vertex closest to fire (if there is more than one candidate, we break ties
	 * by greatest degree) and begin by defending that. We recurse until the total allocated defence is spent. Each
	 * agent has their protection ratings accordingly. When a vertex has a protection rating of 1.0, it moves to the
	 * protected state and cannot contract the infection.
	 * <p>
	 * In the degree based approach, we find the agent which inhabits the highest degree vertex in the graph
	 * underpinning the model (breaking ties this time by proximity to fire) and increase its protection as much as
	 * possible. If there is remaining defence quota after this, we recurse until this allocation is spent. We again
	 * set protection ratings to reflect the strategy to implement and where appropriate re-assign states.
	 * <p>
	 * In the highest protection defence strategy, we find the vertex (or vertices) with highest protection, that is the
	 * vertex (vertices) that would be cheapest to increase their protection rating to 1.0 and move to the protected
	 * state of the model. Then, for as long as there is remaining defence allowance, the strategy reallocates the
	 * remaining quota to the next highest protection rated vertex.
	 *
	 * @param totalDefence the total defence quota that we can implement per turn.
	 * @param whichDefence a value corresponding to the defence strategy to use.
	 * @return the strategy to deploy based on the defence we have chosen.
	 */
	public double[] runDefence(double totalDefence, int whichDefence) {
		// Find the susceptible agents (the only agents we are interested in defending).
		List<Agent> susceptibleAgents = this.getSusceptible();
		double[] strategy = new double[this.getNumVertices()];
		List<Agent> toDefend = switch (whichDefence) {
			case PROXIMITY -> findHighestPeril();
			case DEGREE -> findHighestDegree();
			case PROTECTION -> findHighestProtection();
			default -> throw new IllegalStateException("Unexpected value: " + whichDefence);
		};

		// Split the defence quota evenly among the total vertices we have determined should be defended.
		double eachDefence = totalDefence / toDefend.size();
		loop:
		for (int i = 0; i < this.getNumVertices(); i++) {
			if (toDefend.contains(this.getAgents().get(i))) {
				// If increasing protection by the calculated amount will take protection over 1, take the defence
				// up to 1.0 and then redistribute the remaining defence quota amongst the other agents to protect.
				if (this.getAgents().get(i).getProtection() + eachDefence > 1) {
					double increase = 1 - this.getAgents().get(i).getProtection();
					strategy[i] = increase;
					this.getAgents().get(i).setState(State.PROTECTED);
					susceptibleAgents.remove(this.getAgents().get(i));
					toDefend.remove(this.getAgents().get(i));
					// Update the remaining defence.
					totalDefence -= strategy[i];
				} else {
					strategy[i] += eachDefence;
					break;
				}
				// If we still have defence to use, find the next most appropriate agent(s) and defend them
				while (totalDefence > 0 && !susceptibleAgents.isEmpty()) {
					List<Agent> nextToDefend = switch (whichDefence) {
						case PROXIMITY -> findHighestPeril();
						case DEGREE -> findHighestDegree();
						case PROTECTION -> findHighestProtection();
						default -> throw new IllegalStateException("Unexpected value: " + whichDefence);
					};
					toDefend.addAll(nextToDefend);
					eachDefence = totalDefence / (toDefend.size());
					for (Agent nextDefence : nextToDefend) {
						if (nextDefence.getProtection() + eachDefence > 1) {
							// Again, if increasing protection by the calculated amount will take protection over 1,
							// take the defence up to 1.0 and then redistribute remaining quota.
							double previous = strategy[nextDefence.getVertex()];
							double increase = 1 - nextDefence.getProtection();
							strategy[nextDefence.getVertex()] += increase;
							// Remove fully defended agents from the susceptible state and add to protected state.
							this.getAgents().get(nextDefence.getVertex()).setState(State.PROTECTED);
							susceptibleAgents.remove(nextDefence);
							toDefend.remove(nextDefence);
							totalDefence -= (increase - previous);

						} else {
							strategy[nextDefence.getVertex()] += totalDefence;
							break loop;
						}
					}
				}
			}
		}

		// Get the currently infected vertices, so we can re-calculate peril and assign states.
		int[] fires = new int[this.getNumVertices()];
		List<Agent> onFire = this.getInfected();
		int k = 0;
		for (Agent infected : onFire) {
			fires[k++] = infected.getVertex();
		}
		fires = Arrays.copyOf(fires, k);

		for (int j = 0; j < this.getNumVertices(); j++) {
			agents.get(j).setProtection(agents.get(j).getProtection() + strategy[j]);
			agents.get(j).setState(this.findState(agents.get(j), fires));
		}

		return strategy;
	}

	private List<Agent> findHighestDegree() {
		List<Agent> susceptibleAgents =
				this.getAgents().stream().filter(
						agent -> agent.getState() == State.SUSCEPTIBLE).collect(Collectors.toList());

		List<Agent> highestDegrees = new ArrayList<>();

		int highestDegree = 0;
		for (Agent agent : susceptibleAgents) {
			int thisDegree = Graph.findDegree(this.graph, agent.getVertex());
			if (thisDegree > highestDegree) {
				highestDegree = thisDegree;
			}
		}
		for (Agent agent : susceptibleAgents) {
			int thisDegree = Graph.findDegree(this.graph, agent.getVertex());
			if (thisDegree == highestDegree) {
				highestDegrees.add(agent);
			}
		}
		// Tie-breaker: if we have more than one vertex with highest degree in the graph,
		// Choose the agent with greatest peril rating and defend that one.
		if (highestDegrees.size() > 1) {
			double greatestPeril = 0.0;
			for (Agent agent : highestDegrees) {
				if (agent.getPeril() > greatestPeril) greatestPeril = agent.getPeril();
			}
			for (Agent agent : highestDegrees) {
				if (agent.getPeril() == greatestPeril) {
					// There may be more than one suitable candidate - select the
					// lexicographically first vertex to defend relative agent.
					highestDegrees.clear();
					highestDegrees.add(agent);
					break;
				}
			}
		}
		return highestDegrees;
	}

	/**
	 * In order to determine a reasonable defence, we need to find the agent at highest peril currently. There may be
	 * more than one at this same peril value (e.g. 1.0 is quite a common peril, when an agent is directly adjacent to
	 * an infected agent), so we find all agents with the highest peril and choose which one to return as the agent to
	 * defend by determining which of the choices has the highest degree. If there is more than one such candidate, we
	 * select the lexicographically first agent.
	 *
	 * @return the agent or agents with highest peril from the given list of susceptible agents.
	 */
	private List<Agent> findHighestPeril() {
		List<Agent> susceptibleAgents =
				this.getAgents().stream().filter(
						agent -> agent.getState() == State.SUSCEPTIBLE).collect(Collectors.toList());
		List<Agent> toDefend = new ArrayList<>();
		// Find the agent or agents with highest peril rating(s) in order to increase their protection.
		double highestPeril = 0.0;
		for (Agent agent : susceptibleAgents) {
			if (agent.getPeril() > highestPeril) {
				highestPeril = agent.getPeril();
			}
		}
		for (Agent agent : susceptibleAgents) {
			if (agent.getPeril() == highestPeril) {
				toDefend.add(agent);
			}
		}
		// Tie-breaker: if we have more than one vertex with highest peril rating in the model,
		// Choose the agent at vertex with greatest degree and defend that one.
		if (toDefend.size() > 1) {
			int highestDegree = 0;
			for (Agent agent : toDefend) {
				int thisDegree = Graph.findDegree(this.graph, agent.getVertex());
				if (thisDegree > highestDegree) highestDegree = thisDegree;
			}
			for (Agent agent : toDefend) {
				int thisDegree = Graph.findDegree(this.graph, agent.getVertex());
				if (thisDegree == highestDegree) {
					// There may be more than one suitable candidate - select the
					// lexicographically first vertex to defend relative agent.
					toDefend.clear();
					toDefend.add(agent);
					break;
				}
			}
		}
		return toDefend;
	}

	/**
	 * Some defence strategies may be deployed to the highest protection-rated vertex, moving downwards until we no
	 * longer have protection to deploy.
	 *
	 * @return the agent or agents with highest protection rating from the currently susceptible agents.
	 */
	private List<Agent> findHighestProtection() {
		List<Agent> susceptibleAgents =
				this.getAgents().stream().filter(
						agent -> agent.getState() == State.SUSCEPTIBLE).collect(Collectors.toList());

		List<Agent> highestProtection = new ArrayList<>();
		// Cycle through all agents, reassign highest protection value everytime we find a greater protection rating
		double highProtection = 0.0;
		for (Agent agent : susceptibleAgents) {
			if (agent.getProtection() > highProtection) {
				highProtection = agent.getProtection();
			}
		}
		// Add any agent/agents with the highest protection rating to the list to return
		for (Agent agent : susceptibleAgents) {
			if (agent.getProtection() == highProtection) {
				highestProtection.add(agent);
			}
		}
		return highestProtection;
	}

	/**
	 * Given a rate (probability) of infection, we determine which susceptible vertices contract the infection from any
	 * infected neighbours.
	 *
	 * @param probabilityOfInfection the probability with which the infection spreads.
	 * @return the vertices that the method determines should be burned.
	 */
	public List<Agent> findNextBurning(double probabilityOfInfection) {
		// Find the currently infected (burning) vertices
		List<Agent> infectedAgents = this.getInfected();
		// Find the susceptible agents (the only agents we could infect).
		List<Agent> susceptibleAgents = this.getSusceptible();

		List<Agent> toInfect = new ArrayList<>();
		for (Agent susceptibleAgent : susceptibleAgents) {
			for (Agent infectedAgent : infectedAgents) {
				if (getGraph().isEdge(susceptibleAgent.getVertex(), infectedAgent.getVertex())) {
					if (willInfect(probabilityOfInfection, susceptibleAgent.getProtection())) {
						toInfect.add(susceptibleAgent);
					}
				}
			}
		}
		toInfect.forEach(agent -> agent.setState(State.INFECTED));
		return toInfect.stream().distinct().collect(Collectors.toList());
	}

	/**
	 * Given a probability of infection and the defence rating of the susceptible vertex that that may become infected,
	 * determines whether it will be infected.
	 *
	 * @param probInfection the probability with which the infection propagates.
	 * @param defence       the protection rating of the susceptible agent in peril.
	 * @return whether the agent becomes infected or not.
	 */
	public boolean willInfect(double probInfection, double defence) {
		return (2 - probInfection - (1 - defence) < 1);
	}

	/**
	 * Being a graph (or network) model, we associate each model with a graph object (using the graph class). The graph
	 * a given model utilises is saved and stored as an attribute of the model, hence the use of getters and setters to
	 * access it.
	 *
	 * @return the graph on which the model is based.
	 */
	public Graph getGraph() {
		return graph;
	}

	/**
	 * Setter used to set a graph to the model attribute - that is, to store a graph object that we instantiate as a
	 * permanent attribute to the model we are working on.
	 *
	 * @param graph the graph to associate as an attribute to the current model.
	 */
	public void setGraph(Graph graph) {
		this.graph = graph;
	}

	/**
	 * We maintain a list of agents, one per vertex, that form the basis of the model. Each agent has the usual
	 * attributes of an agent object: a vertex (where it lives), a peril rating, a protection rating and a state
	 * (susceptible, infected, recovered or protected).
	 *
	 * @return the current list of agents.
	 */
	public List<Agent> getAgents() {
		return this.agents;
	}

	/**
	 * We set the list of agents by creating a new agent for each vertex and setting peril and protection initially to
	 * zero and state to susceptible. We then go on to assign the actual ratings and states to each agent once we
	 * initialise the graph and decide on a vertex location for the outbreak to begin at. This method is called only
	 * once, at the start of the method when we do not have an instance of the field yet.
	 *
	 * @param numVertices the number of vertices in the graph model.
	 * @return the list of agents we have created.
	 */
	private List<Agent> assignAgents(int numVertices) {
		List<Agent> agents = IntStream.range(0, numVertices)
				.mapToObj(i -> new Agent(i, 0.0, 0.0, State.SUSCEPTIBLE))
				.collect(Collectors.toList());
		this.agents = agents;
		return agents;
	}

	/**
	 * An important piece of information in the model is the number of vertices in the graph. This informs how many
	 * agents we need to generate to inhabit each vertex and the range of values we can select from to begin the
	 * outbreak.
	 *
	 * @return the assigned number of vertices in the graph model.
	 */
	public int getNumVertices() {
		return getGraph().getNumVertices();
	}

	/**
	 * Several methods benefit from having access to a List of only the susceptible vertices, such as determining the
	 * next defence strategy or which vertices contract the infection in the next time step. This method returns a List
	 * of all currently susceptible vertices.
	 *
	 * @return all agents currently in the susceptible state, as a list of agents.
	 */
	public List<Agent> getSusceptible() {
		List<Agent> agents = this.getAgents();
		List<Agent> susceptibleAgents = new ArrayList<>();

		for (Agent agent : agents) {
			if (agent.getState() == State.SUSCEPTIBLE)
				susceptibleAgents.add(agent);
		}
		return susceptibleAgents;
	}

	/**
	 * In order to monitor and analyse the progression of the model, we need to be able to view which vertices are in
	 * each state - this method returns the agents currently infected by the contagion.
	 *
	 * @return the currently infected agents, as a list of agents.
	 */
	public List<Agent> getInfected() {
		List<Agent> agents = this.getAgents();
		List<Agent> infectedAgents = new ArrayList<>();

		for (Agent agent : agents) {
			if (agent.getState() == State.INFECTED)
				infectedAgents.add(agent);
		}
		return infectedAgents;
	}

	/**
	 * In order to monitor and analyse the progression of the model, we need to be able to view which vertices are in
	 * each state - this method returns the agents that are currently recovered from the contagion, allowing us to
	 * attribute to them some (potentially zero) increase in protection due to some level of immunity, where
	 * appropriate.
	 *
	 * @return the currently recovered agents, as a list of agents.
	 */
	public List<Agent> getRecovered() {
		List<Agent> agents = this.getAgents();
		List<Agent> recoveredAgents = new ArrayList<>();

		for (Agent agent : agents) {
			if (agent.getState() == State.RECOVERED)
				recoveredAgents.add(agent);
		}
		return recoveredAgents;
	}

	/**
	 * In order to monitor and analyse the progression of the model, we need to be able to view which vertices are in
	 * each state - this method returns the currently protected vertices. These are agents who either have a protection
	 * rating of 1.0 (and can thereby not contract the infection) or have no path on the graph to any currently infected
	 * vertex.
	 *
	 * @return the currently protected vertices, as a list of agents.
	 */
	public List<Agent> getProtected() {
		List<Agent> agents = this.getAgents();
		List<Agent> protectedAgents = new ArrayList<>();

		for (Agent agent : agents) {
			if (agent.getState() == State.PROTECTED)
				protectedAgents.add(agent);
		}
		return protectedAgents;
	}

	/**
	 * Cycles through the agents list field and prints them to the standard output. Used for testing purposes.
	 *
	 * @param model the model with agents we want to print.
	 */
	public static String printAgents(Model model) {
		List<Agent> agents = model.getAgents();
		StringBuilder s = new StringBuilder();
		agents.stream().map(agent -> "* " + agent).forEach(s::append);
		return String.valueOf(s);
	}

	/**
	 * Prints arrays to the standard output that contain the vertices of the currently susceptible, infected, recovered
	 * and protected vertices in order to verify that the model is working as expected.
	 */
	private String printSIRP() {
		StringBuilder s = new StringBuilder();
		s.append("\n");
		// Get the vertex locations of currently susceptible agents.
		int[] susceptible = new int[this.getSusceptible().size()];
		Arrays.setAll(susceptible, i -> this.getSusceptible().get(i).getVertex());
		// Get the vertex locations of currently infected agents.
		int[] infected = new int[this.getInfected().size()];
		Arrays.setAll(infected, i -> this.getInfected().get(i).getVertex());
		// Get the vertex locations of currently recovered agents.
		int[] recovered = new int[this.getRecovered().size()];
		Arrays.setAll(recovered, i -> this.getRecovered().get(i).getVertex());
		// Get the vertex locations of currently protected agents.
		int[] defended = new int[this.getProtected().size()];
		Arrays.setAll(defended, i -> this.getProtected().get(i).getVertex());

		// Print each array, as found above, to the standard output
		// to monitor progression of the model.
		s.append(" * S: ").append(Arrays.toString(susceptible)).append("\n * I: ")
				.append(Arrays.toString(infected)).append("\n * R: ").append(Arrays.toString(recovered))
				.append("\n * P: ").append(Arrays.toString(defended));

		return String.valueOf(s);
	}
}
