package com.svmc.mixxgame.screens;

import utils.listener.OnDoneListener;
import utils.screen.AbstractGameScreen;
import utils.screen.GameCore;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Polyline;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.svmc.mixxgame.AssetMap;
import com.svmc.mixxgame.Assets;
import com.svmc.mixxgame.attribute.Constants;
import com.svmc.mixxgame.attribute.EventType;
import com.svmc.mixxgame.attribute.GameEvent;
import com.svmc.mixxgame.attribute.GameState;
import com.svmc.mixxgame.attribute.Level;
import com.svmc.mixxgame.attribute.StringSystem;
import com.svmc.mixxgame.entity.EntityMove;
import com.svmc.mixxgame.entity.GeomatricObject;
import com.svmc.mixxgame.entity.MapParse;
import com.svmc.mixxgame.entity.UserData;

public class GameScreen extends AbstractGameScreen implements ContactListener {
	World					world;
	Box2DDebugRenderer		b2dDebug;
	Viewport				b2dViewport;
	TiledMap				map;
	Body					ground, ball;
	Array<Body>				balls		= new Array<Body>();
	Array<Body>				bodies		= new Array<Body>();
	Array<UserData>			userDatas	= new Array<UserData>();
	Array<GeomatricObject>	listObject	= new Array<GeomatricObject>();

	EntityMove				move;
	Rectangle				goal;

	UIManager				uiManager;
	UISystem				uiSystem;

	public GameScreen(GameCore game) {
		super(game);
	}

	@Override
	public void show() {
		super.show();
		Gdx.gl20.glLineWidth(2f);
		world = new World(new Vector2(0, -10f), true);
		world.setContactListener(this);
		b2dDebug = new Box2DDebugRenderer();
		b2dViewport = new FitViewport(Constants.WIDTH_SCREEN
				* Constants.WORLD_TO_BOX, Constants.HEIGHT_SCREEN
				* Constants.WORLD_TO_BOX);
		createPhysicWorld();
		move = new EntityMove(50, Constants.WIDTH_SCREEN - 50);
		world.getBodies(bodies);
		createWorldData(bodies);
		
		uiSystem = new UISystem(stage);
		uiSystem.create();
		uiSystem.showMenu(new OnDoneListener() {

			@Override
			public void done() {

			}
		});
		uiManager = new UIManager(stage, this);
		setGameState(GameState.RUNING);
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height, true);
		b2dViewport.update((int) width, (int) height, true);
	}

	@Override
	public void render(float delta) {
		super.render(delta);
		b2dDebug.render(world, b2dViewport.getCamera().combined);
	}

	@Override
	public void update(float delta) {
		world.step(1 / 60f, 8, 3);
		move.update(delta);
		if (ball != null) {
			if (direct == 1) {
				target.lerp(max, 0.01f);
				if (target.epsilonEquals(max, .5f)) {
					direct = -1;
				}
			}
			if (direct == -1) {
				target.lerp(min, 0.01f);
				if (target.epsilonEquals(min, .5f)) {
					direct = 1;
				}
			}
			ball.setTransform(target, rotate * MathUtils.degreesToRadians);
			// ball.setTransform(ball.getTransform().getPosition(), (rotate++)
			// * MathUtils.degreesToRadians);
			ball.setLinearVelocity(0, 0);
			ball.setType(BodyType.StaticBody);
		}

		for (UserData userData : userDatas) {
			userData.update(delta);
		}

		if (getGameState() == GameState.RUNING) {
			for (Body body : balls) {
				if (!body.isAwake()) {
					Vector2 ballPosition = new Vector2(body.getTransform()
							.getPosition().x * Constants.BOX_TO_WORLD, body
							.getTransform().getPosition().y
							* Constants.BOX_TO_WORLD);
					if (goal != null && goal.contains(ballPosition)) {
						setGameState(GameState.GAME_COMPLETE);
						uiManager.show();
					}
				}
			}

			if (balls != null && balls.size == Level.MAX_BALL) {
				Body body = balls.get(balls.size - 1);
				if (!body.isAwake()) {
					setGameState(GameState.GAME_OVER);
					uiManager.show();
				}
			}
		}

	}

	@Override
	public void drawBatch(SpriteBatch batch) {
		float delta = Gdx.graphics.getDeltaTime();
		// Assets.instance.fontFactory.getLight20().draw(batch,
		// "FPS : " + Gdx.graphics.getFramesPerSecond(), 30,
		// Constants.HEIGHT_SCREEN - 40);
		//
		// Assets.instance.fontFactory.getLight20().draw(batch,
		// "Ball Remain : " + (Level.MAX_BALL - balls.size + count), 150,
		// Constants.HEIGHT_SCREEN - 40);
		//
		// Assets.instance.fontFactory.getLight20().draw(batch,
		// "Level : " + (Level.LEVEL), 350, Constants.HEIGHT_SCREEN - 40);

		for (UserData userData : userDatas) {
			userData.render(batch, delta);
		}
	}

	
	@Override
	public void drawShapeFill(ShapeRenderer shapeRenderer) {
		for (UserData userData : userDatas) {
			userData.drawShapeFill(shapeRenderer);
		}

		if (starttime != 0) {
			shapeRenderer.rect(20, Constants.HEIGHT_SCREEN - 30,
					(System.currentTimeMillis() - starttime) / 10f, 20);
		}
	}

	@Override
	public void drawShapeLine(ShapeRenderer shapeRenderer) {
		for (UserData userData : userDatas) {
			userData.drawShapeLine(shapeRenderer);
		}
	}

	@Override
	public void beginContact(Contact contact) {

	}

	@Override
	public void endContact(Contact contact) {

	}

	@Override
	public void preSolve(Contact contact, Manifold oldManifold) {

	}

	@Override
	public void postSolve(Contact contact, ContactImpulse impulse) {

	}

	long	starttime	= 0;
	long	endtime		= 0;

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		starttime = System.currentTimeMillis();
		return true;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		endtime = System.currentTimeMillis();
		viewport.unproject(touchPoint.set(screenX, screenY));
		{
			float delta = (endtime - starttime) / 10f;
			// createCircle(touchPoint.x, touchPoint.y, delta);
			if (getGameState() == GameState.RUNING) {
				createCircle(Constants.DEFAULT_X, Constants.DEFAULT_Y, delta);
				starttime = 0;
				endtime = 0;
			}
		}
		return true;
	}

	public Body createCircle(float x, float y, float delta) {
		if (balls.size == Level.MAX_BALL) {
			setGameState(GameState.GAME_OVER);
			uiManager.show();
			return null;
		}
		float radius = 20;
		CircleShape circleShape = new CircleShape();
		circleShape.setRadius(Constants.WORLD_TO_BOX * radius);
		BodyDef bodyDef = new BodyDef();
		bodyDef.type = BodyType.DynamicBody;
		bodyDef.position.set((x) * Constants.WORLD_TO_BOX, (y)
				* Constants.WORLD_TO_BOX);

		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = circleShape;
		fixtureDef.friction = 1f;
		fixtureDef.density = 3f;
		fixtureDef.restitution = .6f;

		Body body = world.createBody(bodyDef);
		body.createFixture(fixtureDef);
		circleShape.dispose();
		body.applyForceToCenter(new Vector2(delta, 0), true);
		balls.add(body);
		return body;
	}

	@Override
	public boolean keyDown(int keycode) {
		if (keycode == Keys.SPACE) {
			reset();
		}
		if (keycode == Keys.ESCAPE) {
			if (uiManager.show) {
				uiManager.disable();
				return true;
			}

			if (!uiManager.show) {
				setGameState(GameState.GAME_COMPLETE);
				uiManager.show();
				return true;
			}
		}

		return super.keyDown(keycode);
	}

	public boolean keyUp(int keycode) {
		return false;
	};

	GameEvent	event	= new GameEvent() {
							@Override
							public void broadcastEvent(EventType type, float x,
									float y) {
							}
						};

	void createPhysicWorld() {
		map = Assets.instance.assetMap.getMap();
		createGround();
	}

	Vector2	min, max, target;
	int		direct	= 1, rotate = 0;
	int		count	= 0;

	void createGround() {
		String layer_enviroment = StringSystem.LAYER_ENVIROMENT;
		if (AssetMap.isContainLayer(map, layer_enviroment)) {
			MapObjects objects = map.getLayers().get(layer_enviroment)
					.getObjects();

			for (MapObject object : objects) {
				if (object.getName() != null
						&& object.getName().equalsIgnoreCase("rotate")) {
					ball = MapParse.instance.createBodyBydefault(world, object);
					MapParse.instance.createDefaultUserData(ball, object);
					min = new Vector2(ball.getPosition().x - 112
							* Constants.WORLD_TO_BOX, ball.getPosition().y);
					max = new Vector2(ball.getPosition().x + 112
							* Constants.WORLD_TO_BOX, ball.getPosition().y);
					target = new Vector2(ball.getPosition().x,
							ball.getPosition().y);
				} else if (object.getName() != null
						&& object.getName().equalsIgnoreCase("target")) {
					if (object instanceof RectangleMapObject) {
						goal = ((RectangleMapObject) object).getRectangle();
					}
				} else if (object.getName() != null
						&& object.getName().equalsIgnoreCase("ball")) {
					Body bd = MapParse.instance.createBodyBydefault(world,
							object);
					MapParse.instance.createDefaultUserData(bd, object);
				} else {
					// Body bd = MapParse.instance.createBodyBydefault(world,
					// object);
					// MapParse.instance.createDefaultUserData(bd, object);
					MapParse.instance.createBodyBydefaultWithUserData(world,
							object);
				}
			}
		}
	}

	Body createCircleBody(EllipseMapObject mapObject) {
		Ellipse ellipse = mapObject.getEllipse();
		CircleShape circleShape = new CircleShape();
		circleShape.setRadius(Constants.WORLD_TO_BOX * ellipse.width / 2);
		BodyDef bodyDef = new BodyDef();
		bodyDef.type = BodyType.DynamicBody;
		bodyDef.position.set((ellipse.x + ellipse.width / 2)
				* Constants.WORLD_TO_BOX, (ellipse.y + ellipse.height / 2)
				* Constants.WORLD_TO_BOX);

		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = circleShape;
		fixtureDef.friction = 1f;
		fixtureDef.density = 3f;
		fixtureDef.restitution = .6f;

		Body body = world.createBody(bodyDef);
		body.createFixture(fixtureDef);
		circleShape.dispose();
		return body;
	}

	Body createRectangleBody(RectangleMapObject mapObject) {
		Rectangle rectagle = mapObject.getRectangle();
		PolygonShape polygonShape = new PolygonShape();
		polygonShape.setAsBox(rectagle.width * Constants.WORLD_TO_BOX / 2,
				rectagle.height * Constants.WORLD_TO_BOX / 2);
		BodyDef bodyDef = new BodyDef();
		bodyDef.type = BodyType.StaticBody;
		bodyDef.position.set((rectagle.x + rectagle.width / 2)
				* Constants.WORLD_TO_BOX, (rectagle.y + rectagle.height / 2)
				* Constants.WORLD_TO_BOX);
		FixtureDef fixtureDef = new FixtureDef();
		fixtureDef.shape = polygonShape;
		Body body = world.createBody(bodyDef);
		body.createFixture(fixtureDef);
		polygonShape.dispose();

		if (mapObject.getProperties().containsKey(StringSystem.TYPE)) {

		}

		return body;
	}

	Body createPolygonBody(PolygonMapObject mapObject) {
		Polygon polygone = (mapObject).getPolygon();
		float[] vertice = polygone.getVertices();

		for (int k = 0; k < vertice.length; k++) {
			if (k % 2 == 0) {
				vertice[k] = Constants.WORLD_TO_BOX
						* (polygone.getX() + vertice[k]);
			} else {
				vertice[k] = Constants.WORLD_TO_BOX
						* (polygone.getY() + vertice[k]);
			}
		}
		PolygonShape polygoneShape = new PolygonShape();
		polygoneShape.set(vertice);

		BodyDef def = new BodyDef();
		def.type = BodyType.StaticBody;
		FixtureDef fDef = new FixtureDef();
		fDef.shape = polygoneShape;
		Body body = world.createBody(def);
		body.createFixture(fDef);
		polygoneShape.dispose();
		return body;
	}

	Body createPolyLineBody(PolylineMapObject mapObject) {
		Polyline polyline = mapObject.getPolyline();

		float[] vertice = polyline.getVertices();

		for (int k = 0; k < vertice.length; k++) {
			if (k % 2 == 0) {
				vertice[k] = Constants.WORLD_TO_BOX
						* (polyline.getX() + vertice[k]);
			} else {
				vertice[k] = Constants.WORLD_TO_BOX
						* (polyline.getY() + vertice[k]);
			}
		}
		ChainShape chain = new ChainShape();
		chain.createChain(vertice);
		BodyDef def = new BodyDef();
		def.type = BodyType.StaticBody;
		FixtureDef fdef = new FixtureDef();
		fdef.shape = chain;
		Body body = world.createBody(def);
		body.createFixture(fdef);
		chain.dispose();
		return body;
	}

	public void createWorldData(Array<Body> bodies) {
		userDatas.clear();
		for (Body body : bodies) {
			if (body.getUserData() != null
					&& body.getUserData() instanceof UserData) {
				userDatas.add((UserData) body.getUserData());
			}
		}
	}

	public void reset() {
		count = 0;
		goal = null;
		ball = null;
		balls.clear();
		world.dispose();
		world = new World(new Vector2(0, -10f), true);
		createPhysicWorld();
		move = new EntityMove(50, Constants.WIDTH_SCREEN - 50);
		world.getBodies(bodies);
		createWorldData(bodies);
		setGameState(GameState.RUNING);
	}
}
