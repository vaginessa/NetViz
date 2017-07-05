package com.wjholden.woac;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class NetViz extends JPanel implements KeyListener {

    static String KEY_BINDINGS = "g: generate random topology\n"
            + "b: generate hub and spoke\n"
            + "q: generate square\n"
            + "e: explode 100 random packets\n"
            + "u: unicast\n"
            + "f: flood\n"
            + "d: drop\n"
            + "n: no loop prevention\n"
            + "h: show help\n"
            + "t: ttl-based loop prevention\n"
            + "s: stp-based loop prevention\n"
            + "+: add a random vertex\n"
            + "-: remove a random vertex\n\n"
            + "left-click on two vertices to to insert an edge\n"
            + "ctrl+left-click+drag to move a vertex\n"
            + "right-click to delete a vertex";

    static final int DEFAULT_DIMENSION = 500;
    static final int PADDING = 50;
    private static final Random RANDOM = new Random();
    private boolean ctrl = false;    
    private Mouse mouse;
    
    private final Timer timer = new javax.swing.Timer(33, this::animate);

    static enum BEHAVIOR {
        DROP,
        UNICAST,
        FLOOD,
    }

    static enum LOOP_PREVENTION {
        NONE,
        STP,
        TTL
    }

    static BEHAVIOR behavior = BEHAVIOR.FLOOD;
    static LOOP_PREVENTION loopPrevention = LOOP_PREVENTION.NONE;

    private List<Vertex> v;
    private Map<Vertex, Set<Vertex>> e;
    private List<Packet> p;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static void createAndShowGUI() {
        JFrame f = new JFrame("NetViz");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        NetViz netviz = new NetViz();
        f.add(netviz);
        f.addKeyListener(netviz);
        f.pack();
        f.setVisible(true);
        netviz.generate();
    }

    private void generateHubAndSpoke() {
        v = new ArrayList<>();
        e = new HashMap<>();
        p = new ArrayList<>();

        int vertexCount = RANDOM.nextInt(24) + 8;
        Vertex root = new Vertex(this.getBounds().width / 2, this.getBounds().height / 2);
        v.add(root);
        e.put(root, new HashSet<>());

        for (int i = 0; i < vertexCount; i++) {
            Vertex vertex = createRandomVertex(v, this.getBounds().width, this.getBounds().height);
            e.get(root).add(vertex);
            e.put(vertex, new HashSet<>());
            e.get(vertex).add(root);
        }

        // now let's randomly create some more edges, up to 2 * |V|
        int extra = RANDOM.nextInt(vertexCount * 2);
        for (int k = 0; k < extra; k++) {
            Vertex start = v.get(RANDOM.nextInt(vertexCount));
            Vertex end;
            do {
                end = v.get(RANDOM.nextInt(vertexCount));
            } while (end == start); // no self-adjacent vertices
            e.get(start).add(end); // no worries if it's already there
        }

        symmetricEdges(v, e);
    }

    private static void symmetricEdges(List<Vertex> v, Map<Vertex, Set<Vertex>> e) {
        // now let's make it a digraph by guaranteeing all the in-edges are
        // symmetric to the out-edges
        e.forEach((start, out) -> { // find each outbound edge
            out.forEach(in -> {     // reference adjacent vertex
                e.get(in).add(start); // add inbound edge for adjacent vertex
            }); // ^ no-op if in-edge already exists in set
        });
    }

    private void generateSquare() {
        v = new ArrayList<>();
        e = new HashMap<>();
        p = new ArrayList<>();

        int x_distance = this.getBounds().width / 5;
        int y_distance = this.getBounds().height / 5;

        for (double x = 0.5f; x < 5; x++) {
            for (double y = 0.5f; y < 5; y++) {
                Vertex vertex = new Vertex((int) (x * x_distance) - Vertex.RADIUS, (int) (y * y_distance) - Vertex.RADIUS);
                e.put(vertex, new HashSet<>());
                v.add(vertex);
            }
        }

        for (int i = 0; i < 24; i++) {
            if (i % 5 < 4) {
                e.get(v.get(i)).add(v.get(i + 1));
            }
            if (i < 20) {
                e.get(v.get(i)).add(v.get((i + 5)));
            }
        }

        symmetricEdges(v, e);
    }
    
    private Vertex createRandomVertex(List<Vertex> v, int w, int h) {
        Vertex vertex = new Vertex(RANDOM.nextInt(w - 2 * PADDING) + PADDING,
                RANDOM.nextInt(h - 2 * PADDING) + PADDING);
        v.add(vertex);
        return vertex;
    }

    private void generate() {
        v = new ArrayList<>();
        e = new HashMap<>();
        p = new ArrayList<>();

        // putting my v's here, yo.
        int vertexCount = RANDOM.nextInt(24) + 8;
        for (int i = 0; i < vertexCount; i++) {
            createRandomVertex(v, this.getBounds().width, this.getBounds().height);
        }

        // each vertex gets at least one adjacency (usually two)
        for (int j = 0; j < vertexCount; j++) {
            Vertex start = v.get(j);
            Vertex end;
            do {
                end = v.get(RANDOM.nextInt(vertexCount));
                //} while (end == start || start.getCenter().distance(end.getCenter()) > 250);
            } while (end == start);
            if (e.containsKey(start)) {
                e.get(start).add(end);
            } else {
                Set<Vertex> out = new HashSet<>();
                out.add(end);
                e.put(start, out);
            }
        }

        // now let's randomly create some more edges, up to 2 * |V|
        int extra = RANDOM.nextInt(vertexCount * 2);
        for (int k = 0; k < extra; k++) {
            Vertex start = v.get(RANDOM.nextInt(vertexCount));
            Vertex end;
            do {
                end = v.get(RANDOM.nextInt(vertexCount));
            } while (end == start); // no self-adjacent vertices
            e.get(start).add(end); // no worries if it's already there
        }

        symmetricEdges(v, e);
    }

    public NetViz() {
        mouse = new Mouse();
        this.addMouseListener(mouse);
        this.addMouseMotionListener(mouse);

        this.setBackground(Color.black);

        timer.start();
    }

    @Override
    public void keyTyped(KeyEvent event) {
        switch (event.getKeyChar()) {
            case 'g':
                generate();
                break;
            case 'b':
                generateHubAndSpoke();
                break;
            case 'h':
                JOptionPane.showMessageDialog(this, KEY_BINDINGS, "Help", JOptionPane.QUESTION_MESSAGE);
                break;
            case 'u':
                behavior = BEHAVIOR.UNICAST;
                break;
            case 'f':
                behavior = BEHAVIOR.FLOOD;
                break;
            case 'd':
                behavior = BEHAVIOR.DROP;
                break;
            case 's':
                loopPrevention = LOOP_PREVENTION.STP;
                break;
            case 't':
                loopPrevention = LOOP_PREVENTION.TTL;
                break;
            case 'n':
                loopPrevention = LOOP_PREVENTION.NONE;
                break;
            case 'q':
                generateSquare();
                break;
            case 'e':
                for (int i = 0 ; i < 100 ; i++) {
                    if (!v.isEmpty())
                        createPacket(getRandomVertex());
                }
                break;
            case '+':
                Vertex newVertex = createRandomVertex(v, this.getBounds().width, this.getBounds().height);
                e.put(newVertex, new HashSet<>());
                if (v.size() > 1) {
                    Vertex closest = findClosest(newVertex);
                    e.get(newVertex).add(closest);
                    e.get(closest).add(newVertex);
                }
                break;
            case '-':
                if (!v.isEmpty()) {
                    removeVertex(getRandomVertex());
                }
                break;
            case KeyEvent.VK_ESCAPE:
                if (mouse.selectedVertex != null) {
                    mouse.selectedVertex.selected = false;
                    mouse.selectedVertex = null;
                }
                break;
        }
    }
    
    Vertex getRandomVertex() {
        if (v.isEmpty())
            return null;
        return v.get(RANDOM.nextInt(v.size()));
    }
    
    void removeVertex(Vertex vertex) {
        v.remove(vertex);
        e.get(vertex).forEach(neighbor -> e.get(neighbor).remove(vertex));
        e.remove(vertex);
    }
    
    Vertex findClosest(Vertex vertex) {
        Vertex closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Vertex n : v) {
            double d = vertex.getCenter().distance(n.getCenter());
            if (n != vertex && (closest == null || d < distance)) {
                closest = n;
                distance = d;
            }
        }
        return closest;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        System.out.println("keyPressed");
        if (e.isControlDown()) {
            ctrl = true;
        }
        System.out.println(Boolean.toString(ctrl));
    }

    @Override
    public void keyReleased(KeyEvent e) {
        System.out.println("keyReleased");
        if (!e.isControlDown()) {
            ctrl = false;
        }
        System.out.println(Boolean.toString(ctrl));
    }

    Vertex getRandomNeighbor(Vertex source) {
        if (v.isEmpty())
            return null;
        
        Object[] adj = e.get(source).toArray();
        Vertex destination = (Vertex) adj[RANDOM.nextInt(adj.length)];
        return destination;
    }
    
    void createPacket(Vertex source) {
        // can't send a packet from a disconnected vertex
        if (e.get(source).isEmpty())
            return;
        
        Packet packet = new Packet(source, getRandomNeighbor(source));
        p.add(packet);
    }

    class Mouse extends MouseAdapter {

        Vertex selectedVertex = null;

        @Override
        public void mouseClicked(MouseEvent event) {
            if (!ctrl && SwingUtilities.isLeftMouseButton(event) && !v.isEmpty()) {
                // we want to create a new edge between two selected vertices
                // states: no vertex selected, start vertex selected, second
                Vertex vertex = getNearest(event.getPoint(), 20);
                if (vertex == null) return; // nothing useful found near selection
                if (selectedVertex == null) {
                    selectedVertex = vertex;
                    selectedVertex.selected = true;
                } else if (vertex != selectedVertex) {
                    e.get(vertex).add(selectedVertex);
                    e.get(selectedVertex).add(vertex);
                    selectedVertex.selected = false;
                } else { // toggle selection of start vertex
                    selectedVertex.selected = false;
                    selectedVertex = null;
                }
            } else if (SwingUtilities.isRightMouseButton(event)) {
                removeVertex(getNearest(event.getPoint(), 20));
                if (selectedVertex != null) {
                    selectedVertex.selected = false;
                    selectedVertex = null;
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            if (ctrl && SwingUtilities.isLeftMouseButton(event) && selectedVertex != null) {
                selectedVertex.x = event.getX();
                selectedVertex.y = event.getY();
                selectedVertex.selected = true;
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (ctrl && SwingUtilities.isLeftMouseButton(event) && selectedVertex != null) {
                selectedVertex.selected = false;
                selectedVertex = null;
            }
        }

        @Override
        public void mousePressed(MouseEvent event) {
            Vertex nearest = getNearest(event.getPoint(), 20);
            if (ctrl)
                selectedVertex = nearest;
        }
    }
    
    Vertex getNearest(Point p, double radius) {
            Vertex nearest = null;
            double d = Double.POSITIVE_INFINITY;
            for (Vertex vertex : v) {
                double dist = p.distance(vertex.getCenter());
                if (dist < d) {
                    nearest = vertex;
                    d = dist;
                }
            }
            if (d > radius) nearest = null;
            return nearest;
        }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(DEFAULT_DIMENSION, DEFAULT_DIMENSION);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); //To change body of generated methods, choose Tools | Templates.
        g.setColor(Color.red);
        e.forEach((start, out) -> {
            out.forEach(end -> {
                Point p1 = start.getCenter();
                Point p2 = end.getCenter();
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
            });
        });
        v.forEach(vertex -> vertex.paintComponent(g));
        p.forEach(packet -> {
            packet.paintComponent(g);
        });

        g.setColor(Color.white);
        int y = g.getClipBounds().height - 20;
        g.drawString(this.toString(), 20, y);
    }

    @Override
    public String toString() {
        return String.format("Behavior: %1$5s     Loop Prevention: %2$4s     |V|=%3$-3d     |E|=%4$-3d     |P|=%5$-7d", behavior.toString(), loopPrevention.toString(), v.size(),
                (e.values().stream().mapToInt(value -> value.size()).sum() / 2), p.size());
    }

    private void animate(ActionEvent evt) {
        float PERCENT_INCREMENT_PER_FRAME = 0.01f;

        if (loopPrevention == LOOP_PREVENTION.TTL) {
            // drop all the packets where TTL=1.
            p.removeIf(packet -> packet.ttl == 0);
        }

        // also drop any packet if the destination vertex has been removed
        p.removeIf(packet -> !v.contains(packet.dst));
        
        
        List<Packet> packetsAtDestination = p.stream().filter(packet -> packet.percentComplete > 1.0f).collect(Collectors.toList());

        switch (behavior) {
            case FLOOD:
                packetsAtDestination.forEach(packet -> {
                    e.get(packet.dst).forEach(dst -> {
                        if (packet.src != dst) {
                            p.add(new Packet(packet, dst));
                        }
                    });
                });
                break;
            case UNICAST: {
                packetsAtDestination.forEach(packet -> {
                    Vertex src = packet.dst;
                    if (e.get(src).size() > 1) {
                        Vertex dst;
                        do {
                            dst = getRandomNeighbor(src);
                        } while (dst == packet.src);
                        Packet newPacket = new Packet(packet, dst);
                        p.add(newPacket);
                    }
                    // else drop:
                    // if the vertex has an outdegree of one then we assume
                    // the unicast packet has nowhere else to go
                });
            }
            break;
        }

        p.removeAll(packetsAtDestination);
        p.forEach(packet -> packet.percentComplete += PERCENT_INCREMENT_PER_FRAME);
        this.repaint();
    }
}

class Vertex extends JComponent implements Comparable<Vertex> {
    boolean selected = false;
    static int RADIUS = 5;

    int x, y, id;
    static int counter = 0;

    public Vertex(int x, int y) {
        this.x = x;
        this.y = y;
        this.id = counter;
        if (this.id % 2 == 1) {
            this.id = -this.id;
        }
        counter++;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (selected) g.setColor(Color.orange);
        else g.setColor(Color.white);
        g.fillOval(x, y, 2 * RADIUS, 2 * RADIUS);
    }

    Point getCenter() {
        Point p = new Point();
        p.x = getCenterX();
        p.y = getCenterY();
        return p;
    }

    int getCenterX() {
        return x + RADIUS;
    }

    int getCenterY() {
        return y + RADIUS;
    }

    int getRadius() {
        return RADIUS;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + this.id;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Vertex other = (Vertex) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Vertex (" + x + ", " + y + ")";
    }

    @Override
    public int compareTo(Vertex o) {
        return Integer.valueOf(this.id).compareTo(o.id);
    }
}

class Packet extends JComponent {
    static final int RADIUS = 4;
    float percentComplete = 0.0f;
    Vertex src, dst;
    int ttl;

    static int DEFAULT_TTL = 4;

    public Packet(Vertex src, Vertex dst) {
        this.src = src;
        this.dst = dst;
        percentComplete += Math.random() / 10.0f;
        ttl = DEFAULT_TTL;
    }

    public Packet(Packet predecessor, Vertex dst) {
        this(predecessor.dst, dst);
        if (NetViz.loopPrevention == NetViz.LOOP_PREVENTION.TTL) {
            ttl = predecessor.ttl - 1;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int x = (int) ((dst.getCenterX() - src.getCenterX()) * percentComplete + src.getCenterX());
        int y = (int) ((dst.getCenterY() - src.getCenterY()) * percentComplete + src.getCenterY());

        g.setColor(Color.blue);
        g.fillOval(x, y, RADIUS, RADIUS);
    }

    @Override
    public String toString() {
        return "Packet " + (percentComplete * 100) + " percent from " + src + " to " + dst;
    }

}
