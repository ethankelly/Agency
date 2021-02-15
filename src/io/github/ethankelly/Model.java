package io.github.ethankelly;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * The {@code Model} class contains all methods involved in storing and updating the state of play. For instance, a
 * method for getting the currently burning vertices (based on a given probability of propagation) and for updating the
 * state (stored as a matrix) of the graph. This matrix keeping information about how the contagion has progressed and
 * begins with one column of all zeros, where each row represents the vertex corresponding to the row index. Another
 * column will represent a defensive move - if any vertices have been influenced to the level required to deem them
 * immune to the disease (perhaps by a vaccine), then they move to the protected state. Then, another column is added to
 * represent any transmissions that occur based on the probability of transmission and the protection rating of the
 * agents the contagion is currently adjacent to. Then, another defence turn is played and so on, until no more moves
 * can be made. This class needs to contain methods that check for this instance after every turn and thereby end the
 * simulation. The methods here are used to pass probabilities which dictate infection dynamics, depending on the
 * particular context we want to consider.
 *
 * @author <a href="mailto:e.kelly.1@research.gla.ac.uk">Ethan Kelly</a>
 */
public class Model {
    private Graph graph;
    private List<Agent> agents;
    private final int numVertices;

    /**
     * Class constructor.
     *
     * @param numVertices the number of vertices in the graph the model is based on.
     * @param graph       the graph we use in the model we are creating.
     */
    public Model(int numVertices, Graph graph) {
        this.numVertices = numVertices;
        this.graph = graph;

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
        double
                leastDist =
                leastDistancePath[fires[0]]; //TODO make this work for more than one fire location - find closest fire, find shortest path to it

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
     * @param agent the agent to determine a protection rating for.
     * @return the updated protection rating attribute.
     */
    public double protectionRating(Agent agent) {
        double peril = agent.getPeril();
        double baseline = Math.random();
        if (baseline * (1 / peril) > 1) {
            return 1;
        } else {
            return peril == 0 ? baseline : baseline * (1 / peril);
        }
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
     * The next defence to implement in the model is determined by first finding the vertex or vertices with the highest
     * peril. Then, the total defence available is shared evenly (for now) between these vertices (or given entirely to
     * one vertex) and then their protection ratings are increased by this evenly split amount. For instance, if we have
     * a defence quota of 1.0 and two vertices with the same highest peril rating, we would increase each of their
     * protection ratings by (up to) 0.5 or 50%. Of course, if their protection rating plus this proposed increase is
     * greater than one, we only need to increase by some fraction of the proposed amount, using the remainder on the
     * next vertex. When a vertex has a protection rating of 1.0, it moves to the protected state and cannot contract
     * the infection. The higher the protection up to this point, the smaller the likelihood of contraction.
     *
     * @param totalDefence the total defence quota that we can implement per turn.
     */
    public void runNaiveNextDefence(double totalDefence) {
        // Find the susceptible agents (the only agents we are interested in defending).
        List<Agent> susceptibleAgents = this.getSusceptible();
        double[] strategy = new double[this.getNumVertices()];
        List<Agent> toDefend = findHighestPeril();

        // Split the defence quota evenly among the total vertices we have determined should be defended.
        double eachDefence = totalDefence / toDefend.size();
        for (int i = 0; i < this.getNumVertices(); i++) {
            if (toDefend.contains(agents.get(i))) {
                if (agents.get(i).getProtection() + eachDefence > 1) {
                    // If increasing protection by the calculated amount will take protection over 1, take the defence
                    // up to 1.0 and then redistribute the remaining defence quota amongst the other agents to protect.
                    strategy[i] = 1 - agents.get(i).getProtection();
                    eachDefence = (totalDefence - strategy[i]) / toDefend.size();
                    agents.get(i).setState(State.PROTECTED);
                    // Remove any fully defended agents from the susceptible list
                    susceptibleAgents.remove(agents.get(i));
                } else strategy[i] = eachDefence;

                double sum = 0;
                for (double v : strategy) sum += v;
                // If we still have defence to use, find the next highest peril agent(s) and defend them
                while (sum < totalDefence && !susceptibleAgents.isEmpty()) {
                    List<Agent> nextToDefend = findHighestPeril();
                    toDefend.addAll(nextToDefend);
                    totalDefence = 1 - sum;
                    eachDefence = totalDefence / nextToDefend.size();
                    for (Agent nextDefence : nextToDefend) {
                        if (nextDefence.getProtection() + eachDefence >= 1) {
                            // Again, if increasing protection by the calculated amount will take protection over 1,
                            // take the defence up to 1.0 and then redistribute remaining quota.
                            strategy[nextDefence.getVertex()] = 1 - nextDefence.getProtection();
                            eachDefence =
                                    (totalDefence - strategy[nextDefence.getVertex()]) / (nextToDefend.size() - 1);
                            agents.get(nextDefence.getVertex()).setState(State.PROTECTED);
                            // Remove any fully defended agents from the susceptible list
                            susceptibleAgents.remove(nextDefence);
                        } else strategy[nextDefence.getVertex()] += eachDefence;
                    }
                    // Recalculate the sum of the strategy elements to see if we've played entire defence quota
                    sum = 0;
                    for (double v : strategy) sum += v;
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
        // Print the strategy we performed (for testing purposes).
        // Each increase is to 2 dp when printed, but maintains full length in usage
        DecimalFormat df = new DecimalFormat("0.00");
        int i = 0;
        double[] strategyToPrint = new double[strategy.length];
        for (double d : strategy) {
            strategyToPrint[i++] = Double.parseDouble(df.format(d));
        }
        StdOut.println("Strategy: " + Arrays.toString(strategyToPrint));

    }

    public void runHighestProtectionDefence(double totalDefence) {
        // Find the susceptible agents (the only agents we are interested in defending).
        List<Agent> susceptibleAgents = this.getSusceptible();
        double[] strategy = new double[this.getNumVertices()];
        List<Agent> toDefend = findHighestDefence();

        // Split the defence quota evenly among the total vertices we have determined should be defended.
        double eachDefence = totalDefence / toDefend.size();
        for (int i = 0; i < this.getNumVertices(); i++) {
            if (toDefend.contains(agents.get(i))) {
                if (agents.get(i).getProtection() + eachDefence > 1) {
                    // If increasing protection by the calculated amount will take protection over 1, take the defence
                    // up to 1.0 and then redistribute the remaining defence quota amongst the other agents to protect.
                    strategy[i] = 1 - agents.get(i).getProtection();
                    eachDefence = (totalDefence - strategy[i]) / toDefend.size();
                    agents.get(i).setState(State.PROTECTED);
                    // Remove any fully defended agents from the susceptible list
                    susceptibleAgents.remove(agents.get(i));
                } else strategy[i] = eachDefence;

                double sum = 0;
                for (double v : strategy) sum += v;
                // If we still have defence to use, find the next highest peril agent(s) and defend them
                while (sum < totalDefence && !susceptibleAgents.isEmpty()) {
                    List<Agent> nextToDefend = findHighestPeril();
                    toDefend.addAll(nextToDefend);
                    totalDefence = 1 - sum;
                    eachDefence = totalDefence / nextToDefend.size();
                    for (Agent nextDefence : nextToDefend) {
                        if (nextDefence.getProtection() + eachDefence >= 1) {
                            // Again, if increasing protection by the calculated amount will take protection over 1,
                            // take the defence up to 1.0 and then redistribute remaining quota.
                            strategy[nextDefence.getVertex()] = 1 - nextDefence.getProtection();
                            eachDefence =
                                    (totalDefence - strategy[nextDefence.getVertex()]) / (nextToDefend.size() - 1);
                            agents.get(nextDefence.getVertex()).setState(State.PROTECTED);
                            // Remove any fully defended agents from the susceptible list
                            susceptibleAgents.remove(nextDefence);
                        } else strategy[nextDefence.getVertex()] += eachDefence;
                    }
                    // Recalculate the sum of the strategy elements to see if we've played entire defence quota
                    sum = 0;
                    for (double v : strategy) sum += v;
                }
            }
        }

        // Get the currently infected vertices, so we can re-calculate peril and assign states.
        // TODO is anything now protected (CAN'T BE ACCESSED BY FIRE)?
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
        // Print the strategy we performed (for testing purposes).
        // Each increase is to 2 dp when printed, but maintains full length in usage
        DecimalFormat df = new DecimalFormat("0.00");
        int i = 0;
        double[] strategyToPrint = new double[strategy.length];
        for (double d : strategy) {
            strategyToPrint[i++] = Double.parseDouble(df.format(d));
        }
        StdOut.println("Strategy: " + Arrays.toString(strategyToPrint));
    }

    /**
     * In order to determine a reasonable defence, we need to find the agent at highest peril currently. There may be
     * more than one at this same peril value (e.g. 1.0 is quite a common peril, when an agent is directly adjacent to
     * an infected agent), so we find all agents with the highest peril in order to increase their defence.
     *
     * @return the agent or agents with highest peril from the given list of susceptible agents.
     */
    private List<Agent> findHighestPeril() {
        List<Agent> agents = this.getAgents();
        List<Agent> susceptibleAgents =
                agents.stream().filter(agent -> agent.getState() == State.SUSCEPTIBLE).collect(Collectors.toList());
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
        return toDefend;
    }

    /**
     * Some defence strategies may be deployed to the highest protection-rated vertex, moving downwards until we no
     * longer have protection to deploy.
     *
     * @return the agent or agents with highest protection rating from the currently susceptible agents.
     */
    private List<Agent> findHighestDefence() {
        List<Agent> agents = this.getAgents();
        List<Agent> susceptibleAgents =
                agents.stream().filter(agent -> agent.getState() == State.SUSCEPTIBLE).collect(Collectors.toList());

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
     */
    public void nextBurning(double probabilityOfInfection) {
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
        if (!toInfect.isEmpty()) {
            System.out.println();
            StdOut.print("INFECTING: ");
            for (Agent agent : toInfect) {
                StdOut.print(agent.getVertex() + " ");
                agent.setState(State.INFECTED);
            }
            StdOut.println();
        }
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
        // This ensures only one instance is created, which we then update, rather than
        // creating multiple instances and so on.
        if (agents == null) {
            agents = setAgents(getNumVertices());
        }
        return agents;
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
    private List<Agent> setAgents(int numVertices) {
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < numVertices; i++) {
            agents.add(new Agent(i, 0.0, 0.0, State.SUSCEPTIBLE));
        }
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
        return numVertices;
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
     */
    private void printAgents() {
        List<Agent> agents = this.getAgents();
        agents.forEach(StdOut::println);
    }

    /**
     * Prints arrays to the standard output that contain the vertices of the currently susceptible, infected, recovered
     * and protected vertices in order to verify that the model is working as expected.
     */
    private void printSIRP() {
        StdOut.println();
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
        StdOut.println("S: " + Arrays.toString(susceptible)
                + "\nI: " + Arrays.toString(infected)
                + "\nR: " + Arrays.toString(recovered)
                + "\nP: " + Arrays.toString(defended));
    }

    /**
     * Runs a test model, with a particular graph and outbreak, so that we can test and monitor the behaviours of each
     * method and verify that the model runs as expected.
     *
     * @param totalDefence           the total amount of protection improvement that can be distributed to susceptible
     *                               vertices each defensive turn.
     * @param probabilityOfInfection the probability with which the infection propagates.
     */
    private void runNaiveTest(double totalDefence, double probabilityOfInfection) {
        this.printSIRP();
        int turn = 0;
        while (true) {
            if (!this.getSusceptible().isEmpty()) {
                this.runNaiveNextDefence(totalDefence);
                this.printSIRP();
                turn++;
            } else {
                StdOut.print("\nNothing more to protect.\nEnding model with "
                        + this.getProtected().size()   + " protected and "
                        + this.getInfected().size()    + " infected vertices in "
                        + turn);
                StdOut.print(turn == 1 ? " turn.\n" : " turns.\n");
                break;
            }
            if (!this.getSusceptible().isEmpty()) {
                this.nextBurning(probabilityOfInfection);
                this.printSIRP();
                turn++;
            } else {
                StdOut.print("\nNothing more to infect.\nEnding model with "
                        + this.getProtected().size()   + " protected and "
                        + this.getInfected().size()    + " infected vertices in "
                        + turn);
                StdOut.print(turn == 1 ? " turn.\n" : " turns.\n");
                break;
            }
        }
    }

    /**
     * Runs a test model, with a particular graph and outbreak, so that we can test and monitor the behaviours of each
     * method and verify that the model runs as expected based on the cheapest defence strategy (defend vertices with
     * the highest protection ratings in descending order to defend as many as possible).
     *
     * @param totalDefence           the total amount of protection improvement that can be distributed to susceptible
     *                               vertices each defensive turn.
     * @param probabilityOfInfection the probability with which the infection propagates.
     */
    private void runCheapestDefenceTest(double totalDefence, double probabilityOfInfection) {
        this.printSIRP();
        int turn = 0;
        while (true) {
            if (!this.getSusceptible().isEmpty()) {
                this.runHighestProtectionDefence(totalDefence);
                this.printSIRP();
                turn++;
            } else {
                StdOut.print("\nNothing more to protect.\nEnding model with "
                        + this.getProtected().size()   + " protected and "
                        + this.getInfected().size()    + " infected vertices in "
                        + turn);
                StdOut.print(turn == 1 ? " turn.\n" : " turns.\n");
                break;
            }
            if (!this.getSusceptible().isEmpty()) {
                this.nextBurning(probabilityOfInfection);
                this.printSIRP();
                turn++;
            } else {
                StdOut.print("\nNothing more to protect.\nEnding model with "
                        + this.getProtected().size()   + " protected and "
                        + this.getInfected().size()    + " infected vertices in "
                        + turn);
                StdOut.print(turn == 1 ? " turn.\n" : " turns.\n");
                break;
            }
        }
    }

    /**
     * Used to test methods involved in running the agency-based SIRP model.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        // Numbers of vertices and edges for testing on random graphs
        int numVertices = 8;
        int numEdges = 16;

        // Split vertices into two partitions for bipartite graphs
        int numVertices1 = numVertices / 2;
        int numVertices2 = numVertices - numVertices1;

        // Probability for Erdős–Rényi graphs
        double p = (double) numEdges / (numVertices * (numVertices - 1) / 2.0);

        // Total defence (improvement in protection ratings) that can be deployed each turn
        double totalDefence = 1.0;

        // Probability with which the infection transmits
        double probabilityOfInfection = 1.0;

        // New graph for use in the model
        Graph g = GraphGenerator.erdosRenyi(numVertices, p);
        StdOut.println("Seed: " + GraphGenerator.getSeed());
        StdOut.println();

        // Initialise the model
        Model m = new Model(numVertices, g);
        StdOut.println(m.getGraph());

        Model n = new Model(numVertices, g);
        StdOut.println(n.getGraph());


        // Cycle through all vertices to test the model using each vertex as a source
        for (int i = 0; i < m.getNumVertices(); i++) {
            StdOut.println("\n *** Outbreak: " + i + " *** \n");
            // Initialise a list of agents given the starting state of the graph
            for (int j = 0; j < m.getNumVertices(); j++) {
                m.getAgents().set(j, new Agent(j, 0, 0, State.SUSCEPTIBLE));
                m.getAgents().get(j).setPeril(m.perilRating(m.getAgents().get(j), new int[]{i}, m.getGraph()));
                m.getAgents().get(j).setProtection(m.protectionRating(m.getAgents().get(j)));
                m.getAgents().get(j).setState(m.findState(m.getAgents().get(j), new int[]{i}));

                // Set the n agents to the same peril and protection ratings as m agents
                // so that we can directly compare the results we obtain when running the models.
                n.getAgents().set(j, new Agent(j, 0, 0, State.SUSCEPTIBLE));
                n.getAgents().get(j).setPeril(m.getAgents().get(j).getPeril());
                n.getAgents().get(j).setProtection(m.getAgents().get(j).getProtection());
                n.getAgents().get(j).setState(n.findState(n.getAgents().get(j), new int[]{i}));
            }
            // Print the agents we initialised, run the model until
            // either nothing can be infected or nothing can be protected.
            StdOut.println("\n ----- NAIVE DEFENCE STRATEGY -----\n");
            m.printAgents();
            m.runNaiveTest(totalDefence, probabilityOfInfection);

            StdOut.println("\n ----- CHEAPEST INCREASE DEFENCE STRATEGY -----\n");
            n.printAgents();
            n.runCheapestDefenceTest(totalDefence, probabilityOfInfection);
        }
        StdOut.close();
    }
}
