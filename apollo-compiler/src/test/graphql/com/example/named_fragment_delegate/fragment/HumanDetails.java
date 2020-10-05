// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.named_fragment_delegate.fragment;

import com.apollographql.apollo.api.GraphqlFragment;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.api.internal.Optional;
import com.apollographql.apollo.api.internal.ResponseFieldMapper;
import com.apollographql.apollo.api.internal.ResponseFieldMarshaller;
import com.apollographql.apollo.api.internal.ResponseReader;
import com.apollographql.apollo.api.internal.ResponseWriter;
import com.apollographql.apollo.api.internal.Utils;
import com.example.named_fragment_delegate.type.CustomType;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HumanDetails implements GraphqlFragment {
  static final ResponseField[] $responseFields = {
    ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
    ResponseField.forString("name", "name", null, false, Collections.<ResponseField.Condition>emptyList()),
    ResponseField.forCustomType("profileLink", "profileLink", null, false, CustomType.URL, Collections.<ResponseField.Condition>emptyList()),
    ResponseField.forObject("friendsConnection", "friendsConnection", null, false, Collections.<ResponseField.Condition>emptyList())
  };

  public static final String FRAGMENT_DEFINITION = "fragment HumanDetails on Human {\n"
      + "  __typename\n"
      + "  name\n"
      + "  profileLink\n"
      + "  friendsConnection {\n"
      + "    __typename\n"
      + "    edges {\n"
      + "      __typename\n"
      + "      node {\n"
      + "        __typename\n"
      + "        name\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}";

  final @NotNull String __typename;

  final @NotNull String name;

  final @NotNull Object profileLink;

  final @NotNull FriendsConnection friendsConnection;

  private transient volatile String $toString;

  private transient volatile int $hashCode;

  private transient volatile boolean $hashCodeMemoized;

  public HumanDetails(@NotNull String __typename, @NotNull String name, @NotNull Object profileLink,
      @NotNull FriendsConnection friendsConnection) {
    this.__typename = Utils.checkNotNull(__typename, "__typename == null");
    this.name = Utils.checkNotNull(name, "name == null");
    this.profileLink = Utils.checkNotNull(profileLink, "profileLink == null");
    this.friendsConnection = Utils.checkNotNull(friendsConnection, "friendsConnection == null");
  }

  public @NotNull String __typename() {
    return this.__typename;
  }

  /**
   * What this human calls themselves
   */
  public @NotNull String name() {
    return this.name;
  }

  /**
   * Profile link
   */
  public @NotNull Object profileLink() {
    return this.profileLink;
  }

  /**
   * The friends of the human exposed as a connection with edges
   */
  public @NotNull FriendsConnection friendsConnection() {
    return this.friendsConnection;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public ResponseFieldMarshaller marshaller() {
    return new ResponseFieldMarshaller() {
      @Override
      public void marshal(ResponseWriter writer) {
        writer.writeString($responseFields[0], __typename);
        writer.writeString($responseFields[1], name);
        writer.writeCustom((ResponseField.CustomTypeField) $responseFields[2], profileLink);
        writer.writeObject($responseFields[3], friendsConnection.marshaller());
      }
    };
  }

  @Override
  public String toString() {
    if ($toString == null) {
      $toString = "HumanDetails{"
        + "__typename=" + __typename + ", "
        + "name=" + name + ", "
        + "profileLink=" + profileLink + ", "
        + "friendsConnection=" + friendsConnection
        + "}";
    }
    return $toString;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof HumanDetails) {
      HumanDetails that = (HumanDetails) o;
      return this.__typename.equals(that.__typename)
       && this.name.equals(that.name)
       && this.profileLink.equals(that.profileLink)
       && this.friendsConnection.equals(that.friendsConnection);
    }
    return false;
  }

  @Override
  public int hashCode() {
    if (!$hashCodeMemoized) {
      int h = 1;
      h *= 1000003;
      h ^= __typename.hashCode();
      h *= 1000003;
      h ^= name.hashCode();
      h *= 1000003;
      h ^= profileLink.hashCode();
      h *= 1000003;
      h ^= friendsConnection.hashCode();
      $hashCode = h;
      $hashCodeMemoized = true;
    }
    return $hashCode;
  }

  public static final class Mapper implements ResponseFieldMapper<HumanDetails> {
    final FriendsConnection.Mapper friendsConnectionFieldMapper = new FriendsConnection.Mapper();

    @Override
    public HumanDetails map(ResponseReader reader) {
      final String __typename = reader.readString($responseFields[0]);
      final String name = reader.readString($responseFields[1]);
      final Object profileLink = reader.readCustomType((ResponseField.CustomTypeField) $responseFields[2]);
      final FriendsConnection friendsConnection = reader.readObject($responseFields[3], new ResponseReader.ObjectReader<FriendsConnection>() {
        @Override
        public FriendsConnection read(ResponseReader reader) {
          return friendsConnectionFieldMapper.map(reader);
        }
      });
      return new HumanDetails(__typename, name, profileLink, friendsConnection);
    }
  }

  /**
   * A connection object for a character's friends
   */
  public static class FriendsConnection {
    static final ResponseField[] $responseFields = {
      ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forList("edges", "edges", null, true, Collections.<ResponseField.Condition>emptyList())
    };

    final @NotNull String __typename;

    final Optional<List<Edge>> edges;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public FriendsConnection(@NotNull String __typename, @Nullable List<Edge> edges) {
      this.__typename = Utils.checkNotNull(__typename, "__typename == null");
      this.edges = Optional.fromNullable(edges);
    }

    public @NotNull String __typename() {
      return this.__typename;
    }

    /**
     * The edges for each of the character's friends.
     */
    public Optional<List<Edge>> edges() {
      return this.edges;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeString($responseFields[0], __typename);
          writer.writeList($responseFields[1], edges.isPresent() ? edges.get() : null, new ResponseWriter.ListWriter() {
            @Override
            public void write(List items, ResponseWriter.ListItemWriter listItemWriter) {
              for (Object item : items) {
                listItemWriter.writeObject(((Edge) item).marshaller());
              }
            }
          });
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "FriendsConnection{"
          + "__typename=" + __typename + ", "
          + "edges=" + edges
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof FriendsConnection) {
        FriendsConnection that = (FriendsConnection) o;
        return this.__typename.equals(that.__typename)
         && this.edges.equals(that.edges);
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= __typename.hashCode();
        h *= 1000003;
        h ^= edges.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static final class Mapper implements ResponseFieldMapper<FriendsConnection> {
      final Edge.Mapper edgeFieldMapper = new Edge.Mapper();

      @Override
      public FriendsConnection map(ResponseReader reader) {
        final String __typename = reader.readString($responseFields[0]);
        final List<Edge> edges = reader.readList($responseFields[1], new ResponseReader.ListReader<Edge>() {
          @Override
          public Edge read(ResponseReader.ListItemReader listItemReader) {
            return listItemReader.readObject(new ResponseReader.ObjectReader<Edge>() {
              @Override
              public Edge read(ResponseReader reader) {
                return edgeFieldMapper.map(reader);
              }
            });
          }
        });
        return new FriendsConnection(__typename, edges);
      }
    }
  }

  /**
   * An edge object for a character's friends
   */
  public static class Edge {
    static final ResponseField[] $responseFields = {
      ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forObject("node", "node", null, true, Collections.<ResponseField.Condition>emptyList())
    };

    final @NotNull String __typename;

    final Optional<Node> node;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public Edge(@NotNull String __typename, @Nullable Node node) {
      this.__typename = Utils.checkNotNull(__typename, "__typename == null");
      this.node = Optional.fromNullable(node);
    }

    public @NotNull String __typename() {
      return this.__typename;
    }

    /**
     * The character represented by this friendship edge
     */
    public Optional<Node> node() {
      return this.node;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeString($responseFields[0], __typename);
          writer.writeObject($responseFields[1], node.isPresent() ? node.get().marshaller() : null);
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "Edge{"
          + "__typename=" + __typename + ", "
          + "node=" + node
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof Edge) {
        Edge that = (Edge) o;
        return this.__typename.equals(that.__typename)
         && this.node.equals(that.node);
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= __typename.hashCode();
        h *= 1000003;
        h ^= node.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static final class Mapper implements ResponseFieldMapper<Edge> {
      final Node.Mapper nodeFieldMapper = new Node.Mapper();

      @Override
      public Edge map(ResponseReader reader) {
        final String __typename = reader.readString($responseFields[0]);
        final Node node = reader.readObject($responseFields[1], new ResponseReader.ObjectReader<Node>() {
          @Override
          public Node read(ResponseReader reader) {
            return nodeFieldMapper.map(reader);
          }
        });
        return new Edge(__typename, node);
      }
    }
  }

  /**
   * A character from the Star Wars universe
   */
  public static class Node {
    static final ResponseField[] $responseFields = {
      ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
      ResponseField.forString("name", "name", null, false, Collections.<ResponseField.Condition>emptyList())
    };

    final @NotNull String __typename;

    final @NotNull String name;

    private transient volatile String $toString;

    private transient volatile int $hashCode;

    private transient volatile boolean $hashCodeMemoized;

    public Node(@NotNull String __typename, @NotNull String name) {
      this.__typename = Utils.checkNotNull(__typename, "__typename == null");
      this.name = Utils.checkNotNull(name, "name == null");
    }

    public @NotNull String __typename() {
      return this.__typename;
    }

    /**
     * The name of the character
     */
    public @NotNull String name() {
      return this.name;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseFieldMarshaller marshaller() {
      return new ResponseFieldMarshaller() {
        @Override
        public void marshal(ResponseWriter writer) {
          writer.writeString($responseFields[0], __typename);
          writer.writeString($responseFields[1], name);
        }
      };
    }

    @Override
    public String toString() {
      if ($toString == null) {
        $toString = "Node{"
          + "__typename=" + __typename + ", "
          + "name=" + name
          + "}";
      }
      return $toString;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof Node) {
        Node that = (Node) o;
        return this.__typename.equals(that.__typename)
         && this.name.equals(that.name);
      }
      return false;
    }

    @Override
    public int hashCode() {
      if (!$hashCodeMemoized) {
        int h = 1;
        h *= 1000003;
        h ^= __typename.hashCode();
        h *= 1000003;
        h ^= name.hashCode();
        $hashCode = h;
        $hashCodeMemoized = true;
      }
      return $hashCode;
    }

    public static final class Mapper implements ResponseFieldMapper<Node> {
      @Override
      public Node map(ResponseReader reader) {
        final String __typename = reader.readString($responseFields[0]);
        final String name = reader.readString($responseFields[1]);
        return new Node(__typename, name);
      }
    }
  }
}
