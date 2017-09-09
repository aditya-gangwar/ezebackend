package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class Counters
{
  private String objectId;
  private Double value;
  private java.util.Date updated;
  private String name;
  private String ownerId;
  private java.util.Date created;
  public String getObjectId()
  {
    return objectId;
  }

  public Double getValue()
  {
    return value;
  }

  public void setValue( Double value )
  {
    this.value = value;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public String getName()
  {
    return name;
  }

  public void setName( String name )
  {
    this.name = name;
  }

  public String getOwnerId()
  {
    return ownerId;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

                                                    
  public Counters save()
  {
    return Backendless.Data.of( Counters.class ).save( this );
  }

  public Future<Counters> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Counters> future = new Future<Counters>();
      Backendless.Data.of( Counters.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<Counters> callback )
  {
    Backendless.Data.of( Counters.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( Counters.class ).remove( this );
  }

  public Future<Long> removeAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Long> future = new Future<Long>();
      Backendless.Data.of( Counters.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( Counters.class ).remove( this, callback );
  }

  public static Counters findById( String id )
  {
    return Backendless.Data.of( Counters.class ).findById( id );
  }

  public static Future<Counters> findByIdAsync( String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Counters> future = new Future<Counters>();
      Backendless.Data.of( Counters.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<Counters> callback )
  {
    Backendless.Data.of( Counters.class ).findById( id, callback );
  }

  public static Counters findFirst()
  {
    return Backendless.Data.of( Counters.class ).findFirst();
  }

  public static Future<Counters> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Counters> future = new Future<Counters>();
      Backendless.Data.of( Counters.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<Counters> callback )
  {
    Backendless.Data.of( Counters.class ).findFirst( callback );
  }

  public static Counters findLast()
  {
    return Backendless.Data.of( Counters.class ).findLast();
  }

  public static Future<Counters> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Counters> future = new Future<Counters>();
      Backendless.Data.of( Counters.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<Counters> callback )
  {
    Backendless.Data.of( Counters.class ).findLast( callback );
  }

  public static BackendlessCollection<Counters> find( BackendlessDataQuery query )
  {
    return Backendless.Data.of( Counters.class ).find( query );
  }

  public static Future<BackendlessCollection<Counters>> findAsync( BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<Counters>> future = new Future<BackendlessCollection<Counters>>();
      Backendless.Data.of( Counters.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<Counters>> callback )
  {
    Backendless.Data.of( Counters.class ).find( query, callback );
  }
}