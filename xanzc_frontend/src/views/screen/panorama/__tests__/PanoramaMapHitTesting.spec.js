import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { pickInteractiveHit, pickRegionSurfaceHit } from '../panoramaMapHit';

describe('PanoramaMap Three 命中筛选', () => {
  it('装饰轮廓线和底座先命中时仍选择同一坐标下的真正城市 surface', () => {
    const line = new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-1, -1, 1), new THREE.Vector3(1, -1, 1), new THREE.Vector3(1, 1, 1)
    ]));
    line.userData = { type: 'region', code: '610300' };
    const baseMesh = new THREE.Mesh(new THREE.BoxGeometry(2, 2, .1));
    baseMesh.position.z = .5;
    baseMesh.userData = { type: 'region', code: '610600', base: true };
    const xianSurface = new THREE.Mesh(new THREE.PlaneGeometry(2, 2));
    xianSurface.userData = { type: 'region', code: '610100' };
    const raycaster = new THREE.Raycaster(new THREE.Vector3(0, 0, 5), new THREE.Vector3(0, 0, -1));
    const hits = raycaster.intersectObjects([line, baseMesh, xianSurface], true);

    expect(pickRegionSurfaceHit(hits, [xianSurface])?.object).toBe(xianSurface);
  });

  it('真正机构点位比地表更近时选择 point，点位在地表后方时才选择 region', () => {
    const surface = new THREE.Mesh(new THREE.PlaneGeometry(2, 2));
    surface.userData = { type: 'region', code: '610100' };
    const nearPoint = new THREE.Mesh(new THREE.SphereGeometry(.25, 8, 6));
    nearPoint.position.z = 2;
    const pointGroup = new THREE.Group();
    pointGroup.userData = { type: 'point', point: { orgCode: 'A' } };
    pointGroup.add(nearPoint);
    const raycaster = new THREE.Raycaster(new THREE.Vector3(0, 0, 5), new THREE.Vector3(0, 0, -1));
    const nearHits = raycaster.intersectObjects([surface, pointGroup], true);
    expect(pickInteractiveHit(nearHits, [surface])).toMatchObject({ kind: 'point', point: { orgCode: 'A' } });

    pointGroup.position.z = -3;
    pointGroup.updateMatrixWorld(true);
    const farHits = raycaster.intersectObjects([surface, pointGroup], true);
    expect(pickInteractiveHit(farHits, [surface])).toMatchObject({ kind: 'region', object: surface });
  });
});
